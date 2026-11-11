package com.k4811.common.can;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.util.Log;

import com.nwd.can.sdk.outer.adil.ICanRemote4OuterFeature;
import com.nwd.can.sdk.outer.adil.ICanRemoteModelCallback;

/**
 * Cliente do serviço CAN da NWD (com.nwd.can.setting/.service.CanService) para o ar-condicionado.
 * Conecta, registra o callback de A/C (addAirCallBack), decodifica os frames em {@link AirState} e
 * deixa enviar ajustes (temperatura, ventilação). Substitui o pedaço de clima da barra inferior da NWD.
 *
 * Nada aqui foi testado numa central com carro: a bancada não tem CAN de clima, então o estado chega
 * vazio/ausente. A UI deve tolerar {@code estado == null} ("sem dados do carro").
 */
public final class CanClient {

    private static final String TAG = "K4811Can";
    private static final String ACTION = "com.nwd.can.service.impl.carcase.ACTION_CAN_SERVICE";
    private static final String PACKAGE = "com.nwd.can.setting";

    public interface Listener {
        /** Novo estado do A/C (nunca null); chamado na thread principal. */
        void onAir(AirState state);

        /** Conectou (true) ou caiu (false) o serviço CAN. */
        void onCanConnected(boolean connected);
    }

    private final Context ctx;
    private final Handler main = new Handler(Looper.getMainLooper());
    private Listener listener;
    private ICanRemote4OuterFeature feature;
    private volatile AirState last;
    private boolean bound;

    public CanClient(Context c) {
        ctx = c.getApplicationContext();
    }

    public void setListener(Listener l) {
        listener = l;
    }

    /** Último estado conhecido (ou null se o carro ainda não informou). */
    public AirState state() {
        return last;
    }

    public boolean connected() {
        return feature != null;
    }

    public void connect() {
        if (bound) return;
        try {
            Intent i = new Intent(ACTION).setPackage(PACKAGE);
            bound = ctx.bindService(i, conn, Context.BIND_AUTO_CREATE);
            if (!bound) Log.w(TAG, "bindService recusou " + PACKAGE);
        } catch (Exception e) {
            Log.w(TAG, "connect", e);
        }
    }

    public void disconnect() {
        if (!bound) return;
        try {
            if (feature != null) feature.removeAirCallBack(airCb);
        } catch (Exception ignored) {
        }
        try {
            ctx.unbindService(conn);
        } catch (Exception ignored) {
        }
        bound = false;
        feature = null;
    }

    // ---- controle --------------------------------------------------------------------------------

    /** Toggle/incremento (ex.: CanProtocol.POWER_SWITCH, AUTO, DUAL, FRONT_DEFOG, FAN_INC, LEFT_TEMP_INC). */
    public void control(byte settingType) {
        send(CanProtocol.packControl(settingType));
    }

    public void fanInc() {
        control(CanProtocol.FAN_INC);
    }

    public void fanDec() {
        control(CanProtocol.FAN_DEC);
    }

    public void leftTempInc() {
        control(CanProtocol.LEFT_TEMP_INC);
    }

    public void leftTempDec() {
        control(CanProtocol.LEFT_TEMP_DEC);
    }

    public void rightTempInc() {
        control(CanProtocol.RIGHT_TEMP_INC);
    }

    public void rightTempDec() {
        control(CanProtocol.RIGHT_TEMP_DEC);
    }

    /** Ventilação absoluta (0..max). */
    public void setFan(int level) {
        send(CanProtocol.packValue(CanProtocol.FAN_VALUE, level));
    }

    /** Envia um frame CAN cru (use os CanProtocol.pack*). */
    public void send(byte[] frame) {
        ICanRemote4OuterFeature f = feature;
        if (f == null || frame == null) return;
        try {
            f.sendCanData(frame);
        } catch (RemoteException e) {
            Log.w(TAG, "sendCanData", e);
        }
    }

    // ---- ligação ao serviço ----------------------------------------------------------------------

    private final ICanRemoteModelCallback.Stub airCb = new ICanRemoteModelCallback.Stub() {
        @Override
        public void onDistributeCanData(byte[] data) {
            AirState s = CanProtocol.parseAir(data);
            if (s == null) return;
            last = s;
            main.post(() -> {
                if (listener != null) listener.onAir(s);
            });
        }
    };

    private final ServiceConnection conn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            feature = ICanRemote4OuterFeature.Stub.asInterface(binder);
            try {
                feature.addAirCallBack(airCb);
            } catch (RemoteException e) {
                Log.w(TAG, "addAirCallBack", e);
            }
            main.post(() -> {
                if (listener != null) listener.onCanConnected(true);
            });
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            feature = null;
            main.post(() -> {
                if (listener != null) listener.onCanConnected(false);
            });
        }
    };
}
