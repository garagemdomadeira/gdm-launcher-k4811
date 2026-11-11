// AIDL do CAN SDK da NWD, reconstruido do com.nwd.statusbarbottom. Ver ICanRemote4OuterCallback.
// O original tem um 2o metodo onDistributeCarInfo(CarInfo) (transaction 2). So declaramos o 1o
// (onDistributeCanData), que e o unico que o servico chama no callback de A/C (addAirCallBack): o
// transaction code 1 bate, e assim evitamos reconstruir o Parcelable CarInfo. Se um dia precisar de
// car info por este callback, declarar onDistributeCarInfo em 2o lugar e criar CarInfo.aidl/.java.
package com.nwd.can.sdk.outer.adil;

interface ICanRemoteModelCallback {
    void onDistributeCanData(in byte[] data);   // 1
}
