#!/bin/bash
port=${1:-8080}
count=0
token=eyJraWQiOiI3M1dmMFBMVFpLUEZFWWQ1X1hLY0JKcHc0VUh1M0FXNWpSY2tiam1JZ1RVIiwiYWxnIjoiUlMyNTYifQ.eyJ2ZXIiOjEsImp0aSI6IkFULm9kT2tsLWI2SWF3V3BpT1NBOUpxc21KQVJUNlNBZkVrZFlsRE9MNjhwcDgiLCJpc3MiOiJodHRwczovL2Rldi0xMzMzMjAub2t0YS5jb20vb2F1dGgyL2RlZmF1bHQiLCJhdWQiOiJhcGk6Ly9kZWZhdWx0IiwiaWF0IjoxNTU5MTI3MDE0LCJleHAiOjE1NTkxMzA2MTQsImNpZCI6IjBvYW15ZWpycDhPbDVacGx1MzU2IiwidWlkIjoiMDB1ZWJseXhvb3dnY2JvTlAzNTYiLCJzY3AiOlsib3BlbmlkIl0sInN1YiI6ImRlbW9Ab2t0YS5jb20ifQ.wCb52qQliU9bszNAY6aBEDVA9tugt5gYouWcV3uFmKV17jsHL43EkYxA1qTT-Fs06qCNgYlyb0Ld3yPjSQNetaFJDPRTBI2nBY3NVIq3Kf26ThUoi6efHiatoHSKM-lTSuJBduQgPvWuEgY_HHTxig1kuj9fO57O6X0euhKySxX413ISoqqUJ5DEB8qBoSUAayC5fcVjgoLsMZPIWBtq-4jPAUexY_FTtC4rhyh-6IEPSxP9cpVpa7Sht_j5BpLuv4pw-uNo6cEAR54dh1QzcGNQTsAoR6xnZZ4RIdAY6i2oE2u342siTfMbqe-D4vmbafiVUh6wj7MS4KjtsUfpBQ

profile () {
  ((count++))
  echo "posting #${count}"
  http POST http://localhost:${port}/profiles email="random${count}" Authorization:"Bearer ${token}"
  if [ $count -gt 120 ]
  then
    echo "count is $count, ending..."
    break
  fi
}

while sleep 1; do profile; done
