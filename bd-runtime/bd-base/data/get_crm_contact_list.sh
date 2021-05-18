DATE=`date '+%Y%m%d'`
FILENAME=/data/crm/$DATE/contact.list.json
mkdir -p /data/crm/$DATE
curl -s https://btr.company.ry/rest/crm.contact.list.json | jq -c '.result|.[]' >$FILENAME
echo "DATE=$DATE"
