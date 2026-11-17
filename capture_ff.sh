#!/system/bin/sh
LOG=/data/local/tmp/ff_network.log
rm -f $LOG
echo "=== FF CLEAN NETWORK CAPTURE STARTED ===" > $LOG
while true; do
    T=$(date +"%T")
    netstat -tunap 2>/dev/null | grep -E "10066|com.dts.freefireth" | while read -r line; do
        echo "[$T] $line" >> $LOG
    done
    sleep 0.5
done
