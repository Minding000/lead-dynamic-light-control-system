# Lead Dynamic Light Control System

## Installation

- Build the project
- Place all files on the server
- Install the dependencies: `sudo apt install build-essential openjdk-21-jdk`
  - Headers:
    - Raspberry Pi: `sudo apt install linux-headers-raspi`
    - Other: `sudo apt install linux-headers-generic`
- WI-FI driver: `cd wifi-driver`
  - Clone: `./wifi-driver/clone.sh`
  - Build: `./wifi-driver/rebuild.sh`
  - Install: `./wifi-driver/reinstall.sh`
- Build: `./build.sh` (Java version has to match installed version)
- Check configuration: `vi configuration.properties`
- Create `tmux` session: `tmux new -s alcs`
- Start: `./run.sh`

## Troubleshooting
- List USB devices: `lsusb`
- List network devices: `lshw -C network`
- List interfaces: `ip link`
- List addresses: `ip addr`
- Ensure the lights are on
- Ensure the WI-FI sticks are installed
- Ensure the drivers are installed (especially after upgrading the kernel)
- Ensure the network adapter names are correct in the netplan and configuration files

## Further reading
JNI guide: https://www3.ntu.edu.sg/home/ehchua/programming/java/javanativeinterface.html
