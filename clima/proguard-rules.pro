# Interfaces AIDL e Parcelables dos serviços da NWD: os nomes precisam ser mantidos.
-keep class com.nwd.** { *; }
-keep class com.bt.** { *; }

# Pilhas de erro legíveis no logcat: sem renomear classes, com nº de linha.
-dontobfuscate
-keepattributes SourceFile,LineNumberTable
