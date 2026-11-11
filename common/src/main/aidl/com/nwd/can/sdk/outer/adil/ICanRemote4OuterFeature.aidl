// AIDL do CAN SDK da NWD (sdk_can V.220707), reconstruido do com.nwd.statusbarbottom decompilado.
// A ORDEM dos metodos = transaction codes (1..28) e precisa bater EXATAMENTE com o servico
// com.nwd.can.setting/.service.CanService (bind via acao com.nwd.can.service.impl.carcase.ACTION_CAN_SERVICE).
// Para A/C usamos so sendCanData (1) e addAirCallBack (9); os demais ficam para manter os codes alinhados.
package com.nwd.can.sdk.outer.adil;

import com.nwd.can.sdk.outer.adil.ICanRemote4OuterCallback;
import com.nwd.can.sdk.outer.adil.ICanRemoteModelCallback;

interface ICanRemote4OuterFeature {
    void sendCanData(in byte[] data);                               // 1
    void initSdkCfg(String a, String b, byte c, String d);          // 2
    void addCallBack4Outerface(ICanRemote4OuterCallback cb);        // 3
    void removeCallBack4Outerface(ICanRemote4OuterCallback cb);     // 4
    void doCheckSumAndWriteData2Uart4CanBox(in byte[] data);        // 5
    byte[] generateNullProtocal(byte a, byte b);                    // 6
    int getProtocalStartOffset();                                   // 7
    byte getDataType(in byte[] data);                               // 8
    void addAirCallBack(ICanRemoteModelCallback cb);                // 9
    void removeAirCallBack(ICanRemoteModelCallback cb);             // 10
    void addDoorCallBack(ICanRemoteModelCallback cb);               // 11
    void removeDoorCallBack(ICanRemoteModelCallback cb);            // 12
    void addSWCAngleCallBack(ICanRemoteModelCallback cb);           // 13
    void removeSWCAngleCallBack(ICanRemoteModelCallback cb);        // 14
    void addRadarCallBack(ICanRemoteModelCallback cb);              // 15
    void removeRadarCallBack(ICanRemoteModelCallback cb);           // 16
    void addCarInfoCallBack(ICanRemoteModelCallback cb);            // 17
    void removeCarInfoCallBack(ICanRemoteModelCallback cb);         // 18
    void addCanVolumeCallBack(ICanRemoteModelCallback cb);          // 19
    void removeCanVolumeCallBack(ICanRemoteModelCallback cb);       // 20
    void addCameraCallBack(ICanRemoteModelCallback cb);             // 21
    void removeCameraCallBack(ICanRemoteModelCallback cb);          // 22
    void addCanDataCallBack(ICanRemoteModelCallback cb);            // 23
    void removeCanDataCallBack(ICanRemoteModelCallback cb);         // 24
    void addCanSettingCallBack(ICanRemoteModelCallback cb);         // 25
    void removeCanSettingCallBack(ICanRemoteModelCallback cb);      // 26
    void addCanCarInfoCallBack(ICanRemoteModelCallback cb);         // 27
    void removeCanCarInfoCallBack(ICanRemoteModelCallback cb);      // 28
}
