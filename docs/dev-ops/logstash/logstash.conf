input {
  tcp {
    mode => "server"
    host => "0.0.0.0"
    port => 4560
    codec => json_lines
    type => "info"
  }
}
filter {}
output {
  elasticsearch {
      action => "index"
      hosts => "es:9200"
      index => "group-buy-market-log-%{+YYYY.MM.dd}"
    }
}
