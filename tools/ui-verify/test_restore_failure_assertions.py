"""Host-only self-test for the failure script's table comparator."""
import json, os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
TABLES=["journal_entries","goals","goal_checkins","advisor_messages","goal_proposals","growth_signals","growth_memories","insight_reports","weekly_reviews","weekly_ai_reviews"]
CORE=[t for t in TABLES if t not in ("weekly_ai_reviews","weekly_reviews")]

from restore_failure_assertions import table_differences


if __name__ == "__main__":
    path=os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))),"docs/reports/G4A2B-acceptance/qa/db-A.json")
    a=json.load(open(path)); b=json.loads(json.dumps(a))
    b["journal_entries"]["rows"][0]["title"]="WRONG"
    assert table_differences(a,b,CORE)==["journal_entries"], table_differences(a,b,CORE)
    b=json.loads(json.dumps(a)); b["weekly_ai_reviews"]["rows"]=[]
    assert table_differences(a,b,CORE)==[], table_differences(a,b,CORE)
    print("SELFTEST PASS: wrong expected table captured; weeklyAI excluded from CORE")
