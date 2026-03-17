# ============================================
#    Script de Arranque - Servicios de Mensajeria
# ============================================
#
# Este archivo contiene scripts para iniciar todos los servicios
# del sistema de mensajeria.
#
# Uso:
#   - Windows: Copia la seccion WINDOWS y guardala como arranque.bat
#   - Linux/Mac: Copia la seccion LINUX y guardala como arranque.sh
#
# ============================================



# ============================================
#           WINDOWS (arranque.bat)
# ============================================

@echo off
echo ============================================
echo    Iniciando Servicios de Mensajeria
echo ============================================
echo.

echo [1/6] Iniciando profile-service (puerto 8088)...
start "profile-service" cmd /k "cd /d %~dp0profile-service && mvn spring-boot:run"

timeout /t 30 /nobreak >nul

echo [2/6] Iniciando chat-service (puerto 8085)...
start "chat-service" cmd /k "cd /d %~dp0chat-service && mvn spring-boot:run"

timeout /t 30 /nobreak >nul

echo [3/6] Iniciando connection-service (puerto 8083)...
start "connection-service" cmd /k "cd /d %~dp0connection-service && mvn spring-boot:run"

timeout /t 30 /nobreak >nul

echo [4/6] Iniciando notification-service (puerto 8084)...
start "notification-service" cmd /k "cd /d %~dp0notification-service && mvn spring-boot:run"

timeout /t 30 /nobreak >nul

REM [5/6] Iniciando media-service (puerto 8089)...
REM start "media-service" cmd /k "cd /d %~dp0media-service && mvn spring-boot:run"

timeout /t 30 /nobreak >nul

echo [6/6] Iniciando api-gateway (puerto 8080)...
start "api-gateway" cmd /k "cd /d %~dp0api-gateway && mvn spring-boot:run"

echo.
echo ============================================
echo    Todos los servicios iniciados
echo ============================================
echo.
echo Puerto    Servicio
echo ------    --------
echo 8088      profile-service
echo 8085      chat-service
echo 8083      connection-service
echo 8084      notification-service
echo 8089      media-service
echo 8080      api-gateway
echo.
echo Presiona cualquier tecla para salir...
pause >nul



# ============================================
#           LINUX/MAC (arranque.sh)
# ============================================

#!/bin/bash

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo "============================================"
echo "   Iniciando Servicios de Mensajeria"
echo "============================================"
echo ""

# Iniciar servicios
echo -e "${YELLOW}[1/6] Iniciando profile-service (8088)...${NC}"
cd profile-service && mvn spring-boot:run > /dev/null 2>&1 &
PROFILE_PID=$!
echo "  PID: $PROFILE_PID"

sleep 20

echo -e "${YELLOW}[2/6] Iniciando chat-service (8085)...${NC}"
cd chat-service && mvn spring-boot:run > /dev/null 2>&1 &
CHAT_PID=$!
echo "  PID: $CHAT_PID"

sleep 20

echo -e "${YELLOW}[3/6] Iniciando connection-service (8083)...${NC}"
cd connection-service && mvn spring-boot:run > /dev/null 2>&1 &
CONN_PID=$!
echo "  PID: $CONN_PID"

sleep 20

echo -e "${YELLOW}[4/6] Iniciando notification-service (8084)...${NC}"
cd notification-service && mvn spring-boot:run > /dev/null 2>&1 &
NOTIF_PID=$!
echo "  PID: $NOTIF_PID"

sleep 20

#echo -e "${YELLOW}[5/6] Iniciando media-service (8089)...${NC}"
#cd media-service && mvn spring-boot:run > /dev/null 2>&1 &
#MEDIA_PID=$!
#echo "  PID: $MEDIA_PID"

sleep 20

echo -e "${YELLOW}[6/6] Iniciando api-gateway (8080)...${NC}"
cd api-gateway && mvn spring-boot:run > /dev/null 2>&1 &
GATEWAY_PID=$!
echo "  PID: $GATEWAY_PID"

cd ..

echo ""
echo "============================================"
echo "   Todos los servicios iniciados"
echo "============================================"
echo ""
echo "Puerto    Servicio           PID"
echo "------    --------           ---"
echo "8088      profile-service    $PROFILE_PID"
echo "8085      chat-service       $CHAT_PID"
echo "8083      connection-service  $CONN_PID"
echo "8084      notification-svc   $NOTIF_PID"
echo "8089      media-service      $MEDIA_PID"
echo "8080      api-gateway        $GATEWAY_PID"
echo ""
echo -e "${GREEN}Presiona Ctrl+C para detener todos los servicios${NC}"

# Cleanup al salir
trap "echo -e '\n${RED}Deteniendo servicios...${NC}'; kill $PROFILE_PID $CHAT_PID $CONN_PID $NOTIF_PID $MEDIA_PID $GATEWAY_PID 2>/dev/null; exit" INT TERM

wait
