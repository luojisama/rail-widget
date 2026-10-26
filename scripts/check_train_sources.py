"""Public, non-ticket source smoke check. Failures are reported without failing APK CI."""
import concurrent.futures
import datetime
import json
import io
import zipfile
import pathlib
import urllib.parse
import urllib.request


def check(name, url, form=None):
    try:
        request = urllib.request.Request(
            url, data=urllib.parse.urlencode(form).encode() if form else None,
            headers={"User-Agent": "Mozilla/5.0"},
        )
        with urllib.request.urlopen(request, timeout=12) as response:
            if name == "路路通数据包":
                raw_package = response.read(32 * 1024 * 1024 + 1)
                if len(raw_package) > 32 * 1024 * 1024:
                    raise ValueError("package too large")
                with zipfile.ZipFile(io.BytesIO(raw_package)) as package:
                    summary = {"bytes": len(raw_package), "entries": package.namelist(),
                               "routes_bytes": package.getinfo("routes.dat").file_size,
                               "jlb_bytes": package.getinfo("jlb.dat").file_size}
                return {"source": name, "http": response.status, "summary": summary}
            raw = response.read(512 * 1024).decode("utf-8-sig", "replace")
            data = json.loads(raw.replace('"name": 应用宝"', '"name": "应用宝"'))
            if name == "路路通版本":
                summary = {"version": data.get("version", {}).get("data")}
            elif name == "12306列车详情":
                detail = data.get("data", {}).get("trainDetail", {}) or {}
                stops = detail.get("stopTime", []) or []
                summary = {"train": detail.get("stationTrainCodeAll"), "stop_count": len(stops),
                           "stop_fields": sorted(stops[0]) if stops else [],
                           "stops": [{key: row.get(key) for key in ("stationName", "stationNo", "distance", "dayDifference", "startTrainDate", "trainDate", "jiaolu_corporation_code", "bureau_code", "wicket", "fact_arraive_date_time", "fact_train_date_time")} for row in stops]}
            else:
                detail = (data.get("content") or {}).get("data") or {}
                summary = {"fields": sorted(detail), "trainStyle": detail.get("trainStyle"),
                           "carInfo": detail.get("carInfo")}
            return {"source": name, "http": response.status, "summary": summary}
    except Exception as error:
        return {"source": name, "error": str(error)}


if __name__ == "__main__":
    today = datetime.datetime.now(datetime.timezone(datetime.timedelta(hours=8))).strftime("%Y%m%d")
    queries = [
        ("路路通版本", "http://down.lltskb.com/android.ver", None),
        ("路路通数据包", "http://down.lltskb.com/an.db", None),
        ("12306列车详情", "https://mobile.12306.cn/wxxcx/wechat/main/travelServiceQrcodeTrainInfo",
         {"trainCode": "G101", "startDay": today}),
        ("12306车型", "https://mobile.12306.cn/wxxcx/openplatform-inner/miniprogram/wifiapps/appFrontEnd/v2/lounge/open-smooth-common/trainStyleBatch/getCarDetail?"
         + urllib.parse.urlencode({"trainCode": "G101", "runningDay": today, "reqType": "form", "carCode": "G101"}), None),
    ]
    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
        results = list(pool.map(lambda args: check(*args), queries))
    output = pathlib.Path("app/build/reports/source-smoke.json")
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(results, ensure_ascii=False, indent=2), encoding="utf-8")
    print(output.read_text(encoding="utf-8"))
