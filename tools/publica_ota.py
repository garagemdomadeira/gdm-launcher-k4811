#!/usr/bin/env python3
"""Publica atualizações da GDM K4811 no GitHub (releases do repositório gdm-k4811-ota), que as centrais procuram uma vez por
semana pelo GDM Atualizações.

Cada publicação é uma release nova com os arquivos novos, o ota.json (a lista completa: o que já estava publicado +
o que entrou agora) e o ota.json.sig (assinatura RSA/SHA-256 com a chave em K4811_mod/ota_keys_gdm; as centrais só aceitam
o ota.json assinado). Os apps e ROMs de releases antigas continuam baixando pelos links de antes.

uso:
  publica_ota.py --apps launcher.apk eq.apk ...            apps (o versionCode tem de subir)
                 --rom gdm:4:4:caminho/rom.zip              ROM do canal (canal:versão:código:arquivo)
                 --notas com.garagemdomadeira.nwd.launcher="texto"  novidades de um app (pacote=texto), repetível
                 --notas-rom "texto"                        novidades da ROM
                 --simular                                  só monta e confere, não publica

Canais de ROM: gdm (horizontal). A ROM precisa ter k4811_ota_canal / k4811_ota_rom no
UpdateConfig.ini e a marca META-INF/com/k4811/platform.sha256 (senão a central não instala sem apagar).
"""
import argparse
import datetime
import hashlib
import json
import os
import re
import subprocess
import sys
import tempfile
import zipfile

REPO = 'guarienteherbert-ux/gdm-k4811-ota'
README_TITULO = '# GDM K4811: atualizações'
README_TEXTO = 'Apps da suíte GDM e ROMs comunidade GDM para a central K4811, lidos pelo app **GDM Atualizações** (procura uma vez por semana).'
KEY = os.path.expanduser(os.environ.get('OTA_KEY', '~/Downloads/K4811_mod/ota_keys_gdm/ota.key.pem'))
SDK = os.path.expanduser(os.environ.get('ANDROID_HOME', '~/Android/Sdk'))


def run(cmd, **kw):
    return subprocess.run(cmd, check=True, text=True, capture_output=True, **kw).stdout


def aapt():
    bt = sorted(os.listdir(os.path.join(SDK, 'build-tools')))[-1]
    return os.path.join(SDK, 'build-tools', bt, 'aapt')


def sha256(path):
    h = hashlib.sha256()
    with open(path, 'rb') as f:
        for b in iter(lambda: f.read(1 << 20), b''):
            h.update(b)
    return h.hexdigest()


def cert_sha256(path):
    """SHA-256 do certificado que assina o APK (o atualizador só oferece a quem tem o mesmo dono)."""
    bt = os.path.dirname(aapt())
    out = run([os.path.join(bt, 'apksigner'), 'verify', '--print-certs', path])
    m = re.search(r'certificate SHA-256 digest: ([0-9a-f]{64})', out)
    if not m:
        sys.exit('não li o certificado de ' + path)
    return m.group(1)


def apk_info(path):
    out = run([aapt(), 'dump', 'badging', path])
    m = re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']*)'", out)
    label = re.search(r"application-label-pt:'([^']*)'", out) or re.search(r"application-label:'([^']*)'", out)
    if not m:
        sys.exit('não li o pacote de ' + path)
    return m.group(1), int(m.group(2)), m.group(3), label.group(1) if label else m.group(1)


def previous():
    """ota.json da última release (lista vazia se ainda não há)."""
    with tempfile.TemporaryDirectory() as d:
        r = subprocess.run(['gh', 'release', 'download', '--repo', REPO, '--pattern', 'ota.json', '-D', d],
                           text=True, capture_output=True)
        p = os.path.join(d, 'ota.json')
        if r.returncode != 0 or not os.path.isfile(p):
            return {'formato': 1, 'apps': [], 'roms': []}
        with open(p, encoding='utf-8') as f:
            return json.load(f)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--apps', nargs='*', default=[])
    ap.add_argument('--rom', action='append', default=[])
    ap.add_argument('--notas', action='append', default=[])
    ap.add_argument('--notas-rom', default='')
    ap.add_argument('--simular', action='store_true')
    ap.add_argument('--republicar', action='store_true',
                    help='nova release com todos os apps já publicados (sem app novo)')
    a = ap.parse_args()
    if not a.apps and not a.rom and not a.republicar:
        sys.exit('nada para publicar (--apps / --rom / --republicar)')
    if not os.path.isfile(KEY):
        sys.exit('sem a chave ' + KEY)

    notes = dict(n.split('=', 1) for n in a.notas)
    tag = 'ota-' + datetime.datetime.now().strftime('%Y.%m.%d-%H%M')
    base = 'https://github.com/%s/releases/download/%s/' % (REPO, tag)
    ota = previous()
    apps = {x['pacote']: x for x in ota.get('apps', [])}
    roms = {x['canal']: x for x in ota.get('roms', [])}
    assets = []
    changelog = []

    for path in a.apps:
        pkg, code, name, label = apk_info(path)
        old = apps.get(pkg)
        if old and code <= old['versionCode']:
            sys.exit('%s: versionCode %d não é maior que o publicado (%d)' % (pkg, code, old['versionCode']))
        asset = '%s-%d.apk' % (pkg, code)
        apps[pkg] = {'pacote': pkg, 'nome': label, 'versionCode': code, 'versionName': name, 'url': base + asset,
                     'sha256': sha256(path), 'tamanho': os.path.getsize(path), 'novidades': notes.get(pkg, ''),
                     'certificado': cert_sha256(path)}
        assets.append((path, asset))
        changelog.append('- %s %s%s' % (label, name, (': ' + notes[pkg]) if pkg in notes else ''))

    for spec in a.rom:
        canal, versao, codigo, path = spec.split(':', 3)
        with zipfile.ZipFile(path) as z:
            try:
                mark = z.read('META-INF/com/k4811/platform.sha256').decode().strip().lower()
            except KeyError:
                sys.exit(path + ': sem a marca META-INF/com/k4811/platform.sha256 (a central apagaria os dados)')
        old = roms.get(canal)
        if old and int(codigo) <= old['codigo']:
            sys.exit('ROM %s: código %s não é maior que o publicado (%d)' % (canal, codigo, old['codigo']))
        if os.path.getsize(path) >= 2 << 30:
            sys.exit(path + ': maior que 2 GB (limite do GitHub por arquivo)')
        asset = 'rom-%s-%s.zip' % (canal, versao)
        print('SHA-256 da ROM (demora)…')
        roms[canal] = {'canal': canal, 'versao': versao, 'codigo': int(codigo), 'url': base + asset,
                       'sha256': sha256(path), 'tamanho': os.path.getsize(path), 'novidades': a.notas_rom,
                       'plataforma': mark}
        assets.append((path, asset))
        changelog.append('- ROM %s (%s)%s' % (versao, canal, (': ' + a.notas_rom) if a.notas_rom else ''))

    # Cada release leva TODOS os apps atuais (não só os novos): quem abre a release no GitHub acha tudo junto. Os que
    # não mudaram são baixados da release anterior (conferindo o SHA-256) e anexados de novo. As ROMs ficam onde estão.
    carry = tempfile.mkdtemp(prefix='ota-carry-')
    novos = {asset for _, asset in assets}
    for pkg, x in sorted(apps.items()):
        asset = '%s-%d.apk' % (pkg, x['versionCode'])
        if asset in novos:
            continue
        old_tag = x['url'].rstrip('/').split('/')[-2]
        run(['gh', 'release', 'download', old_tag, '--repo', REPO, '--pattern', asset, '-D', carry, '--clobber'])
        path = os.path.join(carry, asset)
        if sha256(path) != x['sha256']:
            sys.exit(asset + ': SHA-256 da release anterior não confere')
        x['url'] = base + asset
        if not x.get('certificado'):
            x['certificado'] = cert_sha256(path)
        assets.append((path, asset))

    ota = {'formato': 1, 'gerado': datetime.datetime.now(datetime.timezone.utc).strftime('%Y-%m-%dT%H:%M:%SZ'),
           'apps': sorted(apps.values(), key=lambda x: x['pacote']),
           'roms': sorted(roms.values(), key=lambda x: x['canal'])}
    work = tempfile.mkdtemp(prefix='ota-')
    j = os.path.join(work, 'ota.json')
    with open(j, 'w', encoding='utf-8') as f:
        json.dump(ota, f, ensure_ascii=False, indent=1)
    run(['openssl', 'dgst', '-sha256', '-sign', KEY, '-out', j + '.sig', j])
    pub = os.path.join(work, 'pub.pem')
    run(['openssl', 'pkey', '-in', KEY, '-pubout', '-out', pub])
    run(['openssl', 'dgst', '-sha256', '-verify', pub, '-signature', j + '.sig', j])
    print(open(j, encoding='utf-8').read())
    print('assinatura conferida; release', tag)
    if a.simular:
        print('(simulação: nada publicado; arquivos em %s)' % work)
        return

    if subprocess.run(['gh', 'repo', 'view', REPO], capture_output=True).returncode != 0:
        run(['gh', 'repo', 'create', REPO, '--public', '--description',
             'Atualizações da suíte GDM K4811 e da ROM comunidade GDM (lidas pelo app GDM Atualizações)'])
    if json.loads(run(['gh', 'repo', 'view', REPO, '--json', 'isEmpty']))['isEmpty']:
        # release precisa de um commit: README pela API
        import base64
        readme = ('# GDM K4811: atualizações\n\nReleases lidas pelo app GDM Atualizações das centrais K4811 com a ROM comunidade GDM: '
                  'uma vez por semana ele baixa o `ota.json` da última release, confere a assinatura e pergunta antes '
                  'de instalar apps ou a ROM.\n')
        run(['gh', 'api', '-X', 'PUT', 'repos/%s/contents/README.md' % REPO, '-f', 'message=README',
             '-f', 'content=' + base64.b64encode(readme.encode()).decode()])
    files = []
    for path, asset in assets:
        dst = os.path.join(work, asset)
        os.symlink(os.path.abspath(path), dst)
        files.append(dst)
    files += [j, j + '.sig']
    if not changelog:
        changelog.append('- Mesmos apps da release anterior, todos juntos nesta.')
    run(['gh', 'release', 'create', tag, '--repo', REPO, '--latest', '--title', tag,
         '--notes', '\n'.join(changelog)] + files)
    print('publicado: https://github.com/%s/releases/tag/%s' % (REPO, tag))
    atualiza_readme(ota, tag)


def atualiza_readme(ota, tag):
    """README da página do repositório: as versões atuais com o link de cada arquivo."""
    import base64
    linhas = [README_TITULO, '', README_TEXTO, '',
              'Última publicação: [%s](https://github.com/%s/releases/tag/%s)' % (tag, REPO, tag), '',
              '## Apps', '', '| App | Versão | Pacote | Baixar |', '|---|---|---|---|']
    for x in ota['apps']:
        linhas.append('| %s | %s | `%s` | [APK](%s) |' % (x['nome'], x['versionName'], x['pacote'], x['url']))
    linhas += ['', '## ROMs', '']
    if ota['roms']:
        linhas += ['| Canal | Versão | Baixar |', '|---|---|---|']
        for r in ota['roms']:
            linhas.append('| %s | %s | [ROM](%s) |' % (r['canal'], r['versao'], r['url']))
    else:
        linhas.append('Nenhuma ROM publicada por aqui ainda (a ROM é gravada pelo pen drive na primeira vez).')
    linhas += ['', 'Tudo é conferido pela central: o `ota.json` tem assinatura (`ota.json.sig`) e cada arquivo o seu '
               'SHA-256. Nada é instalado sem tocar em **Confirmar update**.', '']
    conteudo = base64.b64encode('\n'.join(linhas).encode()).decode()
    sha = subprocess.run(['gh', 'api', 'repos/%s/contents/README.md' % REPO, '--jq', '.sha'],
                         text=True, capture_output=True).stdout.strip()
    cmd = ['gh', 'api', '-X', 'PUT', 'repos/%s/contents/README.md' % REPO, '-f', 'message=README: versões atuais',
           '-f', 'content=' + conteudo]
    if sha:
        cmd += ['-f', 'sha=' + sha]
    run(cmd)
    print('README atualizado')


if __name__ == '__main__':
    main()
