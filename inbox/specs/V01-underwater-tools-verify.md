# V01 水中装備 Java 検証
tier: standard
files: src/main/java/com/abyssia/item/ModTools.java, src/main/java/com/abyssia/registry/ModItems.java, src/main/java/com/abyssia/Abyssia.java, src/main/java/com/abyssia/Config.java
goal: 深海合金ツール、潜水ヘルム、フィンの Java 実装を確認し、Forge 1.20.1 API に合わせてコンパイル上の問題を修正する。
constraints: generator と生成リソースは編集しない。Gradle build は実行しない。
accept: 水中採掘補正、呼吸・暗視、泳速補正、登録が一貫し、Java の明白な API・型エラーが残らない。
