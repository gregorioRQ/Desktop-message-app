# Capacidades de Negocio

## Responsabilidad principal
Este servicio gestiona y distribuye notificaciones en tiempo real para la aplicación de mensajería. Resuelve el problema de informar a los usuarios sobre eventos relevantes (mensajes nuevos, contactos añadidos, cambios de estado, etc.) de manera eficiente y escalable, utilizando WebSocket y RabbitMQ para comunicación instantánea.

## Bounded Context (Contexto Delimitado)
Pertenece al contexto de "Notificaciones" dentro del dominio de mensajería. Según DDD, su responsabilidad está delimitada a la gestión, almacenamiento y entrega de notificaciones, sin involucrarse en la lógica de mensajes, usuarios o contactos más allá de lo necesario para notificar.

## Dependencias clave
- RabbitMQ: Para la mensajería y distribución de eventos.
- MySQL: Para el almacenamiento de datos relacionados con notificaciones y usuarios.
- Redis: Para la gestión de presencia de usuarios y optimización de acceso a datos.
- Otros servicios de la aplicación (por ejemplo, servicio de usuarios, servicio de mensajes) para recibir eventos que disparan notificaciones.

## Participación en Flujos Core

Este servicio participa activamente en funcionalidades clave de otros servicios dentro del ecosistema de la aplicación de mensajería. Recibe eventos generados por servicios como el de usuarios y el de mensajes, y actúa en consecuencia para notificar a los usuarios sobre acciones relevantes, como:

- Notificación de mensajes enviados y leídos.
- Actualización de estado de contactos (alta, baja, presencia en línea).
- Sincronización de eventos de usuario (creación, cambios de estado).

De esta manera, el servicio de notificaciones asegura la integración y coherencia de la experiencia de usuario en tiempo real, colaborando con los flujos core de la plataforma.
