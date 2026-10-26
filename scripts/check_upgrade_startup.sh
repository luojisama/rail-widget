#!/usr/bin/env bash
set -euo pipefail
package=team.shiro.railwidget
mkdir -p startup-reports
adb shell am force-stop "$package"
python3 - <<'PY'
import sqlite3, datetime, json
db = sqlite3.connect('legacy-v2.db')
db.executescript('''CREATE TABLE trips (
order_no TEXT PRIMARY KEY, passenger_name TEXT NOT NULL, train_code TEXT NOT NULL,
train_no TEXT, departure_station TEXT NOT NULL, arrival_station TEXT NOT NULL,
departure_date TEXT NOT NULL, departure_time TEXT NOT NULL, arrival_time TEXT,
carriage TEXT, seat TEXT, seat_type TEXT, ticket_gate TEXT, ticket_type TEXT, price TEXT,
stopover_time TEXT, stops_json TEXT, detail_url TEXT, raw_source TEXT, updated_at INTEGER,
is_archived INTEGER DEFAULT 0);
CREATE TABLE settings (key TEXT PRIMARY KEY, value TEXT);
PRAGMA user_version=2;''')
dep = datetime.datetime.now(datetime.timezone(datetime.timedelta(hours=8))) + datetime.timedelta(hours=3)
arr = dep + datetime.timedelta(hours=2)
stops = [{'no':'01','name':'北京朝阳','arr':'----','start':dep.strftime('%H:%M'),'stopover':'始发'},
         {'no':'02','name':'沈阳北','arr':(dep+datetime.timedelta(hours=1)).strftime('%H:%M'),'start':(dep+datetime.timedelta(hours=1, minutes=5)).strftime('%H:%M'),'stopover':'5'},
         {'no':'03','name':'牡丹江','arr':arr.strftime('%H:%M'),'start':'----','stopover':'终到'}]
db.execute('''INSERT INTO trips(order_no,passenger_name,train_code,departure_station,arrival_station,departure_date,departure_time,arrival_time,stops_json,is_archived)
VALUES(?,?,?,?,?,?,?,?,?,0)''', ('legacy-test','测试乘客','G101','北京朝阳','牡丹江',dep.strftime('%Y-%m-%d'),dep.strftime('%H:%M'),arr.strftime('%H:%M'),json.dumps(stops,ensure_ascii=False)))
db.execute("INSERT INTO settings VALUES('island_auto_launch_enabled','1')")
db.commit(); db.close()
PY
adb shell pm clear "$package"
adb push legacy-v2.db /data/local/tmp/railcard-legacy.db
adb shell run-as "$package" mkdir -p databases
adb shell run-as "$package" cp /data/local/tmp/railcard-legacy.db databases/rail_widget.db
adb logcat -c
adb shell am start -W -n "$package/.ui.MainActivity"
sleep 10
adb logcat -d -b crash > startup-reports/upgrade-crash.txt
adb logcat -d > startup-reports/upgrade-logcat.txt
cat startup-reports/upgrade-crash.txt
adb shell pidof "$package"
! grep -q 'FATAL EXCEPTION' startup-reports/upgrade-crash.txt
adb shell uiautomator dump /sdcard/railcard-window.xml
adb pull /sdcard/railcard-window.xml startup-reports/upgrade-window.xml
adb exec-out screencap -p > startup-reports/upgrade-screen.png
adb shell run-as "$package" cat databases/rail_widget.db > startup-reports/upgraded-test.db

# Recovery must remain available even when the normal screen failed on the device.
adb shell am force-stop "$package"
printf 'Diagnostic startup fixture\n' > startup-reports/recovery-fixture.txt
adb push startup-reports/recovery-fixture.txt /data/local/tmp/railcard-crash.txt
adb shell run-as "$package" cp /data/local/tmp/railcard-crash.txt files/last-startup-crash.txt
adb shell am start -W -n "$package/.ui.MainActivity"
sleep 3
adb shell uiautomator dump /sdcard/railcard-recovery.xml
adb pull /sdcard/railcard-recovery.xml startup-reports/recovery-window.xml
grep -q '复制错误信息' startup-reports/recovery-window.xml
adb shell run-as "$package" rm files/last-startup-crash.txt
