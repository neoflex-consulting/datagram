DATE=`date '+%Y%m%d'`
FILENAME=/data/crm/$DATE/contact.list.json
mkdir -p /data/crm/$DATE
curl -s https://b24.nti.work/rest/1251/zsn452mon3mm0m01/crm.contact.list.json | jq -c '.result|.[]' >$FILENAME
echo "DATE=$DATE"
