int led = 13; // 13번 슬롯

void setup() {
  pinMode(led, OUTPUT); // 출력 전용
}

void loop() { // 반복
  digitalWrite(led, HIGH); // 1. ON
  delay(1000); // 1초
  digitalWrite(led, LOW); // 0. OFF
  delay(10000);
}
