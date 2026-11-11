// AIDL do CAN SDK da NWD (sdk_can V.220707), reconstruido do app com.nwd.statusbarbottom decompilado.
// A ordem dos metodos define os transaction codes e precisa bater EXATAMENTE com o servico
// com.nwd.can.setting/.service.CanService. "adil" e o nome do pacote original (nao e typo de "aidl").
package com.nwd.can.sdk.outer.adil;

interface ICanRemote4OuterCallback {
    void onDistributeCanData(in byte[] data);   // 1
    void onDistributeRawData(in byte[] data);   // 2
}
