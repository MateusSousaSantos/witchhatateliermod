# Headless measurement pass — output lands in run/logs/corpus_replay.jsonl and
# run/logs/corpus_crossval.jsonl; read with scripts/audit_replay.py.
# Requires in run/server.properties: function-permission-level=4 (for `stop`) and
# max-tick-time=0 — crossval runs minutes of chamfer on the server thread in this one
# tick, and the default 60s ServerHangWatchdog force-kills the server mid-run.
spell replay-corpus
spell crossval
stop
