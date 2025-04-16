#!/bin/bash
set -eu
cd ./rtl8188eu/
echo "Rebuilding WiFi drivers..."
make
sudo make install
echo "Done."
