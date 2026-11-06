"""Pure DB comparator shared by restore failure runner and its self-test."""
import os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
def table_differences(expected, actual, tables):
    return [table for table in tables if expected.get(table, {}).get("rows") != actual.get(table, {}).get("rows")]


def tables_equal(expected, actual, tables):
    return not table_differences(expected, actual, tables)
