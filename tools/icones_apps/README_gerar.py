import os, sys
sys.path.insert(0, '/home/herbert/Downloads/K4811_mod/final/HTG-icones-novos')
import gera_icones as g

SRC = '/home/herbert/Downloads/K4811_mod/htg_dash/tools/icones/simbolos'
DST = '/tmp/claude-1000/-home-herbert/8fc403c1-b7d8-443d-9fbf-20bddcc7652b/scratchpad/app_icons'
os.makedirs(DST, exist_ok=True)

# pacote -> (simbolo, cor de cima, cor de baixo)
MAP = {
    'com.nwd.radio':            ('radio',         '#FF6B6B', '#C92A3A'),
    'com.nwd.audioset':         ('equalizer',     '#7C8CFF', '#3B3FD6'),
    'com.nwd.android.music.ui': ('library_music', '#34D399', '#0F8A5F'),
    'com.nwd.bt.music':         ('headphones',    '#4DA3FF', '#0B4FCF'),
    'com.nwd.android.phone':    ('call',          '#3FD98A', '#0E8F5A'),
    'com.nwd.cpumonitor':       ('mode_fan',      '#3FD9C3', '#0E8F86'),
    'com.nwd.car.setting':      ('settings',      '#A9B3BF', '#5E6873'),
}

for pkg, (sym, top, bot) in MAP.items():
    g.icone(os.path.join(SRC, sym + '.svg'), top, bot, os.path.join(DST, pkg + '.png'), 256)
    print(pkg)
