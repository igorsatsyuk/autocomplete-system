redis.call("FT.CREATE", "idx:autocomplete",
  "ON", "HASH",
  "PREFIX", "1", "autocomplete:",
  "SCHEMA",
  "query", "TEXT",
  "score", "NUMERIC", "SORTABLE"
)
return "OK"