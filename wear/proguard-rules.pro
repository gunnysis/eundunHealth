# Wear OS 모듈 R8 규칙 (룰 12 & 룰 15 준수).
-keepattributes Signature, *Annotation*

# :core:wear-contract 직렬화/계약 데이터 클래스 및 PassiveListenerService 기본 생성자 보존
-keep class com.gunnys.eundunhealth.wear.contract.** { <init>(...); *; }
-keep class com.gunnys.eundunhealth.wear.sensor.EundunPassiveListenerService { <init>(); }
-keep class com.gunnys.eundunhealth.wear.sync.PhoneSyncCommandListenerService { <init>(); }
