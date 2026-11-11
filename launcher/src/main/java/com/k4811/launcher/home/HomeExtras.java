package com.k4811.launcher.home;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.k4811.common.home.GpsClient;
import com.k4811.common.home.HomeGrid;
import com.k4811.common.ui.Ui;
import com.k4811.launcher.R;
import com.k4811.launcher.car.Car3DView;
import com.k4811.launcher.car.CarPhoto;
import com.k4811.launcher.obd.Dtc;
import com.k4811.launcher.obd.ObdClient;
import com.k4811.launcher.obd.ObdData;

import java.io.File;
import java.util.Locale;

/**
 * Módulos Carro e OBD2 da tela inicial.
 *
 * <p>Carro: o carro 3D (modelo, cor e placa) ou uma foto; tocar abre as opções. Com o OBD2 (ou o GPS) as rodas giram
 * conforme a velocidade.
 *
 * <p>OBD2: com um ELM327 pareado (nome com OBD, ELM, VHM ou CAR), velocidade, marcha estimada com a sugestão de troca,
 * RPM, luz de injeção e códigos de defeito, temperatura do motor, bateria e combustível. Sem ELM327, a velocidade do GPS
 * e os vitais apagados. Tocar mostra os códigos de defeito. Só lê com a tela inicial à vista e um dos módulos na tela.
 */
public final class HomeExtras {
    private static final long OBD_RECHECK_MS = 5000;

    private final Activity activity;
    private final HomeModuleViews modules;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final GpsClient gps;
    private final Car3DView car3d;
    private final ImageView carPhoto;
    private final TextView carEmpty;
    private final View obdBlock;
    private final TextView speed;
    private final TextView time;
    private final TextView date;
    private final TextView state;
    private final TextView gear;
    private final TextView rpm;
    private final TextView mil;
    private final TextView temp;
    private final TextView volts;
    private final TextView fuel;
    private final ImageView milIcon;
    private String carPath;
    private boolean resumed;
    private boolean obdRunning;
    private boolean gpsRunning;
    private ObdData last;

    private final Runnable obdRecheck = this::startObd;

    public HomeExtras(Activity activity, HomeModuleViews modules) {
        this.activity = activity;
        this.modules = modules;
        gps = new GpsClient(activity);
        car3d = (Car3DView) activity.findViewById(R.id.car_3d);
        carPhoto = (ImageView) activity.findViewById(R.id.car_photo);
        carEmpty = (TextView) activity.findViewById(R.id.car_empty);
        carEmpty.setTypeface(Ui.regular(activity));
        activity.findViewById(R.id.car_block).setOnClickListener(v -> Car3DView.showConfigDialog(activity,
            this::renderCar, () -> CarPhoto.pick(activity, this::renderCar)));

        obdBlock = activity.findViewById(R.id.obd_block);
        speed = value(R.id.obd_speed);
        time = text(R.id.obd_time);
        time.setTypeface(Ui.semiBold(activity));
        date = text(R.id.obd_date);
        date.setTypeface(Ui.regular(activity));
        state = text(R.id.obd_state);
        state.setTypeface(Ui.regular(activity));
        gear = value(R.id.obd_gear);
        rpm = value(R.id.obd_rpm);
        mil = text(R.id.obd_mil);
        mil.setTypeface(Ui.regular(activity));
        temp = value(R.id.obd_temp);
        volts = value(R.id.obd_volts);
        fuel = value(R.id.obd_fuel);
        for (int id : new int[] {R.id.obd_speed_icon, R.id.obd_gear_icon, R.id.obd_rpm_icon, R.id.obd_temp_icon,
            R.id.obd_volts_icon, R.id.obd_fuel_icon}) {
            ((ImageView) activity.findViewById(id)).setColorFilter(Ui.TEXT_MUTED);
        }
        milIcon = (ImageView) activity.findViewById(R.id.obd_mil_icon);
        milIcon.setColorFilter(Ui.TEXT_MUTED);
        obdBlock.setOnClickListener(v -> showCodes());
        obdBlock.setOnLongClickListener(v -> {
            ObdClient.get().resetGears();
            android.widget.Toast.makeText(activity, R.string.home_obd_gears_reset, android.widget.Toast.LENGTH_LONG).show();
            return true;
        });
    }

    private final java.text.SimpleDateFormat timeFormat = new java.text.SimpleDateFormat("HH:mm", Locale.US);
    private final Runnable clockTick = new Runnable() {
        @Override
        public void run() {
            java.util.Date now = new java.util.Date();
            time.setText(timeFormat.format(now));
            date.setText(android.text.format.DateFormat.format(
                android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEdMMM"), now));
            handler.postDelayed(this, 1000L - (System.currentTimeMillis() % 1000L) + 10);
        }
    };

    public void resume() {
        resumed = true;
        handler.removeCallbacks(clockTick);
        handler.post(clockTick);
        renderCar();
        startObd();
    }

    public void pause() {
        resumed = false;
        handler.removeCallbacks(obdRecheck);
        handler.removeCallbacks(clockTick);
        car3d.stop();
        stopObd();
        stopGps();
    }

    public void release() {
        pause();
        handler.removeCallbacksAndMessages(null);
    }

    private boolean showing(HomeGrid.Module m) {
        return modules.slotOf(m) != null;
    }

    // ------------------------------------------------------------------ carro

    private void renderCar() {
        if (!showing(HomeGrid.Module.CAR)) {
            car3d.stop();
            return;
        }
        if (Car3DView.use3D(activity)) {
            car3d.loadPlate(activity);
            car3d.setVisibility(View.VISIBLE);
            if (resumed) car3d.start();
            carPhoto.setVisibility(View.GONE);
            carEmpty.setVisibility(View.GONE);
            return;
        }
        car3d.stop();
        car3d.setVisibility(View.GONE);
        File f = CarPhoto.file(activity);
        String path = f == null ? null : f.getAbsolutePath() + ":" + f.lastModified();
        if (path == null || !path.equals(carPath)) {
            carPath = path;
            Bitmap b = null;
            if (f != null) {
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inSampleSize = 2;
                b = BitmapFactory.decodeFile(f.getAbsolutePath(), o);
            }
            carPhoto.setImageBitmap(b);
        }
        boolean has = carPhoto.getDrawable() != null && f != null;
        carPhoto.setVisibility(has ? View.VISIBLE : View.GONE);
        carEmpty.setVisibility(has ? View.GONE : View.VISIBLE);
    }

    // ------------------------------------------------------------------ OBD2 / GPS

    private void startObd() {
        handler.removeCallbacks(obdRecheck);
        if (!resumed) return;
        boolean wanted = showing(HomeGrid.Module.OBD) || showing(HomeGrid.Module.CAR);
        if (!wanted) {
            stopObd();
            stopGps();
            return;
        }
        if (ObdClient.available()) {
            stopGps();
            if (!obdRunning) {
                obdRunning = true;
                ObdClient.get().setListener(this::renderObd);
                ObdClient.get().start(activity);
            }
            return;
        }
        stopObd();
        startGps();
        handler.postDelayed(obdRecheck, OBD_RECHECK_MS);
    }

    private void stopObd() {
        if (!obdRunning) return;
        obdRunning = false;
        ObdClient.get().setListener(null);
        ObdClient.get().stop();
    }

    private void startGps() {
        if (gpsRunning) return;
        gpsRunning = true;
        renderNoObd(null);
        gps.setListener(this::renderNoObd);
        gps.start();
    }

    private void stopGps() {
        if (!gpsRunning) return;
        gpsRunning = false;
        gps.stop();
        gps.setListener(null);
    }

    /** Sem ELM327: só a velocidade do GPS; os vitais ficam "--" e a linha de cima explica. */
    private void renderNoObd(GpsClient.Fix fix) {
        String none = activity.getString(R.string.home_obd_none);
        boolean live = fix != null && !Float.isNaN(fix.speedKmh);
        float kmh = live && fix.moving() ? fix.speedKmh : 0f;
        speed.setText(live ? activity.getString(R.string.home_obd_kmh, Math.round(kmh)) : none);
        state.setText(gps.available() ? R.string.home_obd_no_device_gps : R.string.home_obd_no_device);
        state.setVisibility(View.VISIBLE);
        for (TextView t : new TextView[] {gear, rpm, temp, volts, fuel}) t.setText(none);
        mil.setText(R.string.home_obd_off);
        mil.setTextColor(Ui.TEXT_MUTED);
        milIcon.setColorFilter(Ui.TEXT_MUTED);
        setCellsAlpha(0.45f);
        car3d.setTelemetry(kmh, 0, kmh > 1f ? 1 : 0);
    }

    private void renderObd(ObdData d) {
        last = d;
        String none = activity.getString(R.string.home_obd_none);
        boolean live = d.state == ObdData.State.CONNECTED;
        car3d.setTelemetry(live && d.speed >= 0 ? d.speed : 0f, live && d.rpm >= 0 ? d.rpm : 0, live ? d.gear : 0);
        state.setText(live ? "" : activity.getString(R.string.home_obd_connecting));
        state.setVisibility(live ? View.GONE : View.VISIBLE);
        setCellsAlpha(live ? 1f : 0.5f);
        speed.setText(live && d.speed >= 0 ? activity.getString(R.string.home_obd_kmh, d.speed) : none);
        rpm.setText(live && d.rpm >= 0 ? activity.getString(R.string.home_obd_rpm, d.rpm) : none);
        fuel.setText(d.fuel >= 0 ? d.fuel + "%" : none);
        volts.setText(!Float.isNaN(d.volts) ? String.format(Locale.getDefault(), "%.1f V", d.volts) : none);
        temp.setText(d.coolant != Integer.MIN_VALUE ? d.coolant + "°C" : none);
        int codes = d.dtcs == null ? -1 : d.dtcs.length;
        if (codes > 0 || d.mil) {
            mil.setText(codes > 0 ? activity.getResources().getQuantityString(R.plurals.home_obd_codes, codes, codes)
                : activity.getString(R.string.home_obd_mil_on));
            mil.setTextColor(Ui.WARNING);
            milIcon.setColorFilter(Ui.WARNING);
        } else {
            mil.setText(codes == 0 ? activity.getString(R.string.home_obd_ok) : none);
            mil.setTextColor(Ui.TEXT_SOFT);
            milIcon.setColorFilter(Ui.TEXT_MUTED);
        }
        String g = !live || d.gear < 0 ? none
            : d.gear == 0 ? activity.getString(R.string.home_obd_neutral)
            : activity.getString(R.string.home_obd_gear, d.gear);
        if (!live || d.shift == 0) {
            gear.setText(g);
            return;
        }
        // Sugestão de troca colada à marcha: "4↑", "3↓".
        SpannableString s = new SpannableString(g + (d.shift > 0 ? "↑" : "↓"));
        s.setSpan(new ForegroundColorSpan(Ui.SUCCESS), g.length(), s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        gear.setText(s);
    }

    private void setCellsAlpha(float a) {
        for (int id : new int[] {R.id.obd_rpm_cell, R.id.obd_row2, R.id.obd_row3}) {
            activity.findViewById(id).setAlpha(a);
        }
    }

    /** Toque no OBD2: os códigos de defeito lidos, com a explicação de cada um. */
    private void showCodes() {
        StringBuilder msg = new StringBuilder();
        if (last == null) {
            msg.append(activity.getString(ObdClient.available() ? R.string.home_obd_connecting
                : R.string.home_obd_no_device));
        } else if (last.dtcs == null || last.dtcs.length == 0) {
            msg.append(activity.getString(last.mil ? R.string.home_obd_mil_on : R.string.home_obd_no_codes));
        } else {
            for (Dtc c : last.dtcs) {
                if (msg.length() > 0) msg.append("\n\n");
                msg.append(c.code).append(c.pending ? " (" + activity.getString(R.string.home_obd_pending) + ")" : "")
                    .append('\n').append(c.describe(activity));
            }
        }
        new AlertDialog.Builder(activity, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setTitle(R.string.home_obd_codes_title)
            .setMessage(msg)
            .setPositiveButton(android.R.string.ok, null)
            .show();
    }

    private TextView text(int id) {
        return (TextView) activity.findViewById(id);
    }

    private TextView value(int id) {
        TextView t = text(id);
        t.setTypeface(Ui.medium(activity));
        return t;
    }
}
