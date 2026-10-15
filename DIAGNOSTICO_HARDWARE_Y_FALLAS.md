# Diagnóstico de Hardware y Análisis de Fallas: RM-014S Físico vs. Android Nativo

Este documento recopila la investigación técnica profunda sobre por qué el control remoto físico **RM-014S / RM-014S+** funciona con el televisor mientras que la aplicación Android nativa (`rm014s_android_native`) en smartphones con IR Blaster (como el **Tecno Camon 30** u otros equipos MediaTek) aún no logra accionar el equipo.

---

## 1. Comparativa de Hardware: Control Físico vs. Smartphone

| Característica | Control Remoto Físico (RM-014S) | Smartphone Android (IR Blaster) | Impacto en el Televisor |
| :--- | :--- | :--- | :--- |
| **Alimentación** | 2 pilas AAA en serie (3.0 V directos a transistor). | Línea regulada de 1.8 V / 3.3 V del SoC. | Mucho menor energía disponible para el pulso en el celular. |
| **Corriente de pico en el LED** | **200 mA a 800 mA** en pulsos ultracortos. | **15 mA a 30 mA** (limitada por protección de batería). | La intensidad lumínica del celular es de 10x a 20x menor. |
| **Lente / Ángulo de emisión** | Diodo de 5mm con domo de enfoque (20°–30°). | Diodo SMD plano milimétrico (difusión abierta sin lente). | El haz del teléfono se dispersa y pierde alcance rápidamente (>1.5 m falla). |
| **Longitud de onda óptica** | **940 nm** estricta (estándar de electrónica de consumo). | Varía según fabricante (en algunos SoCs 850 nm o 940 nm). | El filtro oscuro del receptor TSOP del TV rechaza o atenúa longitudes distintas a 940 nm. |
| **Generación de la portadora** | Oscilador cerámico / cuarzo en silicio (37.91 kHz exactos). | Modulador PWM por divisor de reloj de software en el SoC. | Desviaciones de ±3 kHz en el teléfono hacen que el filtro pasabanda del TV bloquee la señal. |

---

## 2. Factores Críticos del Sistema Operativo y Drivers (MediaTek / Android)

### A. Bug de Polaridad Invertida del Driver (Active High vs. Active Low)
* **Especificación Android (`ConsumerIrManager.transmit`):**
  * Índice `0` = Tiempo de encendido de la portadora (Mark / ON).
  * Índice `1` = Tiempo de silencio (Space / OFF).
* **Falla documentada en SoCs MediaTek (Tecno, Infinix, algunos Xiaomi):**
  * En ciertas versiones del kernel de MediaTek, el driver del temporizador PWM invierte la lógica física del pin GPIO.
  * Al enviar una trama NEC `[9000, 4500, ...]`, el teléfono apaga el emisor durante 9000 µs y lo enciende durante 4500 µs.
  * Como el receptor del televisor espera un pulso de luz inicial de 9 ms para sincronizar su circuito de control automático de ganancia (AGC), al recibir oscuridad simplemente ignora todo el paquete.

### B. Asincronismo de `ConsumerIrManager.transmit()` y Colisión de Tramas
* En el código actual:
  ```kotlin
  for (frame in if (firstFrameOnly) p.frames.take(1) else p.frames) {
      withContext(Dispatchers.IO) { irManager?.transmit(freq, frame.pattern) }
      if (frame.gapUs > 0) delay((frame.gapUs + 999) / 1000L)
  }
  ```
* En muchas arquitecturas Android, `transmit()` **no es bloqueante**: encola el buffer hacia el demonio del HAL y retorna casi instantáneamente.
* Si el hardware aún está expulsando los pulsos por DMA cuando se ejecuta el `delay` en Kotlin, la segunda llamada a `transmit()` (el código de repetición de 110 ms) se envía antes de tiempo o colisiona con el controlador ocupado, provocando que el driver la descarte en silencio sin arrojar excepción.

### C. Exigencia de Repetición del Firmware del TV
* Los microcontroladores de placas de TV (placas chinas tipo CX-507, MStar, CV9203, Realtek) implementan algoritmos de debouncing óptico para evitar disparos accidentales generados por lámparas fluorescentes o luz solar directa.
* Muchos firmwares de TV exigen **al menos 2 o 3 tramas continuas idénticas** o la trama principal acompañada de su código de repetición exacto a 110 ms. Si la app emite una sola trama aislada (*firstFrameOnly* o sin repetición fluida), el televisor la desecha por seguridad.

---

## 3. Protocolo de Pruebas y Diagnóstico Paso a Paso

Para determinar la causa exacta en tu televisor y equipo:

### Paso 1: Verificación de Emisión Óptica (Test de Cámara)
1. Abre la cámara de **otro teléfono celular** (preferiblemente la **cámara frontal**, ya que la mayoría de cámaras traseras modernas tienen filtro físico *IR-Cut* que bloquea el infrarrojo).
2. Apunta el emisor IR de tu teléfono directamente al lente de la cámara en un ambiente con poca luz.
3. Activa en la app la función `transmitCameraTestSignal()` o pulsa repetidamente cualquier tecla.
4. **Resultado esperado:** En la pantalla de la cámara debes ver un destello visible de color violeta / azul claro. Si no ves nada, el driver no está activando el diodo o el hardware no tiene permisos.

### Paso 2: Prueba de Distancia Crítica (10 cm)
1. Coloca el teléfono a **10 o 15 centímetros** del sensor receptor del TV (justo al lado del LED de standby de la pantalla), apuntando de forma totalmente perpendicular.
2. Prueba las teclas de Power o Volumen.
3. **Interpretación:**
   * Si a 10 cm funciona pero a 2 metros no: El problema es **potencia óptica y ángulo de dispersión** del LED SMD del celular.
   * Si a 10 cm tampoco responde en ningún código: El problema es de **protocolo, polaridad invertida o descarte de tramas en el driver**.

### Paso 3: Prueba de Inversión de Polaridad
Si el LED emite luz pero el televisor no responde ni a 10 cm, se debe probar enviar un arreglo con un silencio falso previo o invertir el estado inicial del pulso para verificar si el driver del SoC MediaTek invierte los niveles lógicos del hardware.
