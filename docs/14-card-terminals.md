# 14. Terminales de pago con tarjeta

Cobro con tarjeta en **terminal física** desde Caja (POS) y Kiosko. Las
terminales y las cuentas bancarias son **del restaurante** — la plataforma
nunca toca el dinero ni las credenciales del proveedor salen del backend.

## Decisiones base (válidas para cualquier proveedor)

- El **backend** es quien inicia el cobro (nunca el browser): calcula el
  monto desde la orden, guarda las credenciales del proveedor y recibe el
  resultado.
- Config de pago **por cliente (tenant)**: cada restaurante elige su
  proveedor y registra sus credenciales.
- Cada `Device` (POS/KIOSK) se asocia a **su terminal física**.
- Entidad `Payment` por intento de cobro: orden, dispositivo, proveedor,
  referencia externa, monto, estatus, autorización y últimos 4 dígitos.
- Regla de cocina: órdenes `CARD` entran a KDS **solo con pago aprobado**;
  `CASH` entra directo como hoy.

## Las opciones (elegir una por restaurante)

| Proveedor | Cómo se conecta | Depósito | Onboarding | Ideal si… |
|---|---|---|---|---|
| **Manual / standalone** | El cajero teclea el monto en su terminal actual | Su banco actual | Nada | Ya tiene terminal y no quiere integración |
| **BBVA Total POS** | LAN/TCP-IP: agente local en el equipo | Cuenta BBVA directo | Contrato + instalación BBVA (3-5 días) | Quiere quedarse 100% con BBVA |
| **Billpocket Ultra** | Nube (push por internet) | Cuenta BBVA directo | Self-serve, compra terminal | BBVA pero sin trámite bancario |
| **Mercado Pago Point** | Nube (push por internet) | Wallet MP → transferencia a banco | Self-serve, sandbox inmediato | Quieren la opción más rápida/barata |
| **NetPay Smart** | Nube (push por internet) | Cualquier banco vía SPEI | Terminal de pruebas + certificación | Vendor orientado a POS multi-tenant |

> Todas soportan propina, cancelación y devolución. Ninguna terminal física
> sirve entre proveedores: cada una solo funciona con su propio servicio.

---

## Opción A — BBVA Total POS (terminal bancaria integrada)

Producto oficial de BBVA México que integra su lector de tarjetas con el
software del comercio por **TCP/IP en la red local**. Soporta modo
**restaurante** (propina y pospropina), retail y hotel, además de MSI,
contactless y DCC. Costo: $300 de apertura única + tasa de descuento
(~1.30% débito / ~1.82% crédito promedio según giro).

### Pasos del cliente (restaurante)

1. Contactar a su ejecutivo de cuenta BBVA (o sucursal) y contratar
   **Total POS** en modalidad **restaurante**.
2. Dar de alta: RFC/datos fiscales, CLABE BBVA de depósito, afiliación.
3. **Pregunta clave al banco:** ¿su terminal actual (ej. Ingenico
   Move/3500) puede enrolarse como lector Total POS, o instalan pin-pads
   nuevos?
4. Pedir explícitamente:
   - La **especificación del protocolo de integración** (no es pública —
     la entregan al contratar).
   - Ambiente de pruebas y proceso de **certificación**.
   - Si BBVA provee un conector/driver ya hecho con API local, o hay que
     implementar el protocolo TCP/IP directamente.
5. BBVA realiza la instalación (3-5 días hábiles): plataforma, enlace y
   afiliaciones.
6. En sus equipos: **Windows** conectado en red local al lector, impresora
   para el pagaré.

### Pasos de la plataforma (nosotros)

1. **Esperar el spec del protocolo** — no hay nada que programar contra
   Total POS hasta tenerlo.
2. Mientras tanto, construir la base común (sirve para todos los
   proveedores): `Client.paymentProvider`, asociación `Device`↔terminal,
   entidad `Payment`, regla KDS.
3. **Agente local por equipo**: servicio chico en Windows que
   - expone `http://localhost:PUERTO` para que el front mande "cobra $X",
   - habla TCP/IP con el lector usando el protocolo BBVA,
   - devuelve aprobado/rechazado y reporta el resultado a la API en nube.
4. Instalador del agente por estación + certificación con BBVA.

```text
Kiosko/Caja (browser) → agente local (localhost) → lector BBVA (TCP/IP, LAN)
        │                      │
        └── back en Railway ◄──┘  (agente reporta el resultado)
```

### Riesgos / pendientes

- El protocolo puede ser complejo o requerir certificación formal — los
  tiempos dependen de BBVA, no de nosotros.
- Cada estación con cobro integrado necesita el agente instalado (paso
  extra de operación por restaurante).
- Ventaja grande: depósito directo a la cuenta BBVA del restaurante y
  propina nativa — para un cliente que no quiere cambiar de banco, es la
  opción que menos fricción comercial tiene.

---

## Opción B — Billpocket Ultra (BBVA, sin trámite bancario)

Billpocket es de BBVA pero se contrata como producto aparte. La **Cloud
Terminal API** manda el cobro por internet a su lector (Ultra / Ultra P /
Ultra SE); el cliente confirma en la terminal y el resultado llega por
webhook. Depósito directo a la CLABE del restaurante.

### Pasos del cliente

1. Crear cuenta Billpocket y comprar la(s) terminal(es) **Ultra**.
2. Obtener su **user token** (empieza con el ambiente Dev — sin dinero
   real).
3. En la app: ir a Configuración → Pagos → registrar credenciales y asignar
   el serial de cada terminal a su dispositivo (POS/KIOSK).
4. Configurar el webhook en el dashboard de Kushki apuntando a
   `https://<backend>/api/payments/webhook`.

### Pendiente técnico

- La API está marcada **beta** y solo opera en México — confirmar con el
  ejecutivo Billpocket.
- El cobro llega como notificación que alguien **toca en la terminal**
  ("COBRAR") — en kiosko lo toca el cliente; hay que diseñar bien esa
  pantalla.

---

## Opción C — Mercado Pago Point

La más rápida de probar: sandbox real sin ejecutivo (`x-test-scope:
sandbox`), docs excelentes. Push por internet a sus terminales Point
(Smart/Mini, hardware PAX).

### Pasos del cliente

1. Crear cuenta Mercado Pago y comprar terminal **Point**.
2. Configurar la terminal en modo integrado (PDV) con su tienda y caja en
   el panel MP — un PDV = una terminal.
3. Obtener el **Access Token** productivo y registrarlo en la app.
4. El dinero cae al **wallet Mercado Pago**; se transfiere a su CLABE
   (manual o automático programado, sin costo) — es el paso extra vs.
   depósito bancario directo.

---

## Opción D — NetPay Smart

Adquirente mexicano orientado a vendors de POS (así integran Soft
Restaurant y otros). REST push a su terminal **Smart** (PAX A910S).
Deposita a la cuenta bancaria del comercio vía SPEI — incluido BBVA.

### Pasos del cliente

1. Crear cuenta desarrollador y comprar terminal **Smart** de pruebas.
2. Obtener credenciales sandbox + app PinPad DEV.
3. Integración → **certificación** de NetPay → alta como comercio.
4. En producción depositan a la CLABE que den de alta.

### Pendiente técnico

- Onboarding más largo (certificación formal) — pero su modelo ya está
  pensado para plataformas multi-tenant como la nuestra.

---

## Opción E — Modo standalone (cero integración, ya funciona)

El POS actual ya soporta `CARD` manual: el cajero ve el total en pantalla,
lo teclea en su terminal bancaria (la Move/3500 que ya tienen, o cualquiera)
y confirma en el sistema. La terminal procesa por su cuenta.

- **Sin costo, sin integración, sin hardware nuevo** — es lo que hace la
  mayoría de comercios chicos.
- Desventajas: doble captura del monto, sin conciliación automática.
- Mejora mínima recomendada: campo opcional de **número de autorización**
  del voucher para conciliar cortes.
- Sirve como **fallback** permanente: si el proveedor integrado está caído
  o el restaurante aún no compra la terminal, siempre puede cobrar así.

---

## Qué se construye en la plataforma (independiente del proveedor)

1. `Client.paymentProvider`: `NONE | TOTAL_POS | BILLPOCKET | MERCADOPAGO |
   NETPAY` + credenciales del proveedor (solo servidor, nunca en responses).
2. `Device` ↔ terminal física (serial del lector o ID de estación/agente).
3. `Payment` por intento: referencia propia que el proveedor nos regresa
   (clave de idempotencia y reconciliación).
4. Interfaz interna por proveedor: `iniciar`, `estatus`, `cancelar`,
   `reembolsar` — una implementación por vendor, resuelta según el tenant.
5. Kiosko/Caja: pantalla "pasa tu tarjeta" → espera → aprobado / rechazado
   / cancelado / timeout, con reintento y cambio a efectivo.
6. Casos a cubrir: doble click, abandono, terminal apagada, webhook
   perdido (reconciliación por polling), cancelación antes/después de
   autorizar, devolución total/parcial.

## Recomendación

- **Cliente que no quiere cambiar de BBVA:** Total POS — depósito directo
  y propina nativa; el costo es el trámite + agente local + certificación.
- **Onboarding rápido y escalable a muchos restaurantes:** cloud-push
  (Billpocket para quedarse en BBVA, Mercado Pago para probar ya, NetPay
  para modelo vendor multi-tenant).
- El diseño soporta ambos modelos — no se excluyen entre sí.
