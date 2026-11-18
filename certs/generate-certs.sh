#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "${SCRIPT_DIR}"

echo "Generating server development keypair and certificate..."
keytool -genkeypair -alias campus-server -keyalg RSA -keysize 2048 -validity 365 \
  -keystore server.keystore.p12 -storetype PKCS12 -storepass changeit \
  -dname "CN=localhost, OU=CampusConnect, O=CampusConnect, L=Campus, ST=State, C=US" \
  -ext "SAN=dns:localhost,ip:127.0.0.1"

echo "Exporting server certificate..."
keytool -exportcert -alias campus-server -keystore server.keystore.p12 -storepass changeit -file server.cer

echo "Importing server certificate into client truststore..."
keytool -importcert -noprompt -alias campus-server -file server.cer -keystore server.truststore.p12 -storetype PKCS12 -storepass changeit

echo "Generating untrusted server keystore (for testing rejection of untrusted certificates)..."
keytool -genkeypair -alias untrusted-server -keyalg RSA -keysize 2048 -validity 365 \
  -keystore untrusted.keystore.p12 -storetype PKCS12 -storepass changeit \
  -dname "CN=untrusted.example.com, OU=Rogue, O=Untrusted, L=Unknown, ST=Unknown, C=US" \
  -ext "SAN=dns:untrusted.example.com,ip:127.0.0.1"

rm -f server.cer
echo "TLS certificates and keystores successfully generated in certs/ directory."
