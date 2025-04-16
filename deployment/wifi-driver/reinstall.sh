#!/bin/bash
set -eu
cd ./rtl8188eu/
echo "Reinstalling WiFi drivers..."
sudo modprobe -rv 8188eu || true
sudo modprobe -v 8188eu
sudo netplan apply
echo "Done."
