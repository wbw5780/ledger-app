#!/usr/bin/env bash
# 把 /root/ledger-app/ledger.apk 发布到 GitHub Releases（wbw5780/ledger-app）。
# 用法：/root/publish-ledger.sh ["changelog 文本"]
# - tag_name = versionCode，name = v<versionName>，body = $1
# - 同 tag 的 release 已存在则先删除（release + tag），可重复执行
# - token 从 ~/.git-credentials 读取，只经 stdin 传给 curl（不出现在参数/日志/输出中）
set -euo pipefail
set +x

OWNER=wbw5780
REPO=ledger-app
APK=/root/ledger-app/ledger.apk
AAPT=/opt/android-sdk/build-tools/35.0.0/aapt
API="https://api.github.com/repos/$OWNER/$REPO"
CHANGELOG="${1:-版本更新：修复问题并改进体验}"

die() { echo "错误：$*" >&2; exit 1; }

[ -f "$APK" ] || die "找不到 $APK"
[ -x "$AAPT" ] || die "找不到 aapt：$AAPT"

TOKEN="$(grep -m1 "@github.com" ~/.git-credentials 2>/dev/null | sed -E 's#^https?://[^:]+:([^@]+)@.*#\1#' || true)"
[ -n "$TOKEN" ] || die "~/.git-credentials 中没有 github.com 的 token"

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

# 带认证的 curl：Authorization 头经 stdin 的 curl 配置传入（-K -），不出现在进程参数里
gh() {
    printf 'header = "Authorization: token %s"\n' "$TOKEN" | curl -sS -K - \
        -H "Accept: application/vnd.github+json" -H "X-GitHub-Api-Version: 2022-11-28" "$@"
}
json() { python3 -I -c "import json,sys; d=json.load(open(sys.argv[1])); print($2)" "$1"; }

# 1. 版本信息
BADGING="$("$AAPT" dump badging "$APK" | grep -m1 '^package:')"
VC="$(sed -E "s/.*versionCode='([0-9]+)'.*/\1/" <<<"$BADGING")"
VN="$(sed -E "s/.*versionName='([^']+)'.*/\1/" <<<"$BADGING")"
PKG="$(sed -E "s/.*name='([^']+)'.*/\1/" <<<"$BADGING")"
SIZE="$(stat -c %s "$APK")"
[[ "$VC" =~ ^[0-9]+$ ]] || die "无法读取 versionCode"
echo "APK：$PKG  versionCode=$VC  versionName=$VN  size=$SIZE"

# 2. 删除已存在的同 tag release 与 tag
code="$(gh -o "$TMP/old.json" -w '%{http_code}' "$API/releases/tags/$VC")"
if [ "$code" = "200" ]; then
    OLD_ID="$(json "$TMP/old.json" 'd["id"]')"
    echo "已存在 tag $VC 的 release（id=$OLD_ID），先删除"
    code="$(gh -o /dev/null -w '%{http_code}' -X DELETE "$API/releases/$OLD_ID")"
    [ "$code" = "204" ] || die "删除 release 失败 HTTP $code"
fi
code="$(gh -o /dev/null -w '%{http_code}' -X DELETE "$API/git/refs/tags/$VC")"
case "$code" in
    204) echo "已删除旧 tag $VC" ;;
    404|422) ;;  # 不存在
    *) die "删除 tag 失败 HTTP $code" ;;
esac

# 3. 创建 release（tag 指向 main 最新提交）
python3 -I -c '
import json,sys
print(json.dumps({"tag_name":sys.argv[1],"target_commitish":"main","name":"v"+sys.argv[2],
  "body":sys.argv[3],"draft":False,"prerelease":False,"make_latest":"true"}, ensure_ascii=False))
' "$VC" "$VN" "$CHANGELOG" > "$TMP/req.json"
code="$(gh -o "$TMP/rel.json" -w '%{http_code}' -X POST -H "Content-Type: application/json" \
    --data-binary @"$TMP/req.json" "$API/releases")"
[ "$code" = "201" ] || { cat "$TMP/rel.json" >&2; die "创建 release 失败 HTTP $code"; }
REL_ID="$(json "$TMP/rel.json" 'd["id"]')"
UPLOAD_URL="$(json "$TMP/rel.json" 'd["upload_url"].split("{")[0]')"
echo "已创建 release id=$REL_ID  tag=$VC  name=v$VN"

# 4. 上传资产 ledger.apk
code="$(gh -o "$TMP/asset.json" -w '%{http_code}' -X POST \
    -H "Content-Type: application/vnd.android-package-archive" \
    --data-binary @"$APK" "$UPLOAD_URL?name=ledger.apk")"
[ "$code" = "201" ] || { cat "$TMP/asset.json" >&2; die "上传资产失败 HTTP $code"; }
echo "已上传 ledger.apk（$(json "$TMP/asset.json" 'd["size"]') 字节）"

# 5. 自检：匿名访问 releases/latest（与 App 相同的请求方式，不带 token）
sleep 2
curl -sS -H "Accept: application/vnd.github+json" "$API/releases/latest" -o "$TMP/latest.json"
python3 -I - "$TMP/latest.json" "$VC" "v$VN" "$SIZE" <<'PY'
import json, sys
d = json.load(open(sys.argv[1])); vc, name, size = sys.argv[2], sys.argv[3], int(sys.argv[4])
a = next((x for x in d.get("assets", []) if x.get("name") == "ledger.apk"), None)
print("releases/latest：tag_name=%s name=%s" % (d.get("tag_name"), d.get("name")))
if a:
    print("  asset=%s size=%s content_type=%s" % (a["name"], a["size"], a.get("content_type")))
    print("  url=%s" % a["browser_download_url"])
ok = d.get("tag_name") == vc and d.get("name") == name and a is not None and a["size"] == size
print("自检：" + ("通过" if ok else "失败"))
sys.exit(0 if ok else 1)
PY
