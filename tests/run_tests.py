#!/usr/bin/env python3
"""
The Lads Client - Master E2E Test Suite Runner
Executes Tiers 1-4 tests with filtering, detailed reporting, JSON export,
and exit code status for CI/CD and verification pipelines.
"""

import os
import sys
import time
import json
import argparse
import unittest
from pathlib import Path

# Ensure UTF-8 output encoding on Windows consoles
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
if hasattr(sys.stderr, "reconfigure"):
    sys.stderr.reconfigure(encoding="utf-8", errors="replace")

# Add tests directory to sys.path
TESTS_DIR = Path(__file__).resolve().parent
REPO_ROOT = TESTS_DIR.parent
if str(TESTS_DIR) not in sys.path:
    sys.path.insert(0, str(TESTS_DIR))


def load_tier_suites(tiers, feature=None):
    """Loads unittest test suites based on selected tiers and optional feature filter."""
    loader = unittest.TestLoader()
    suite = unittest.TestSuite()
    
    tier_module_map = {
        1: "test_tier1_feature_coverage",
        2: "test_tier2_boundary_corner",
        3: "test_tier3_cross_feature",
        4: "test_tier4_real_world"
    }
    
    for t in tiers:
        mod_name = tier_module_map.get(t)
        if not mod_name:
            continue
        try:
            mod = __import__(mod_name)
            tier_suite = loader.loadTestsFromModule(mod)
            
            # Apply feature filter if specified
            if feature:
                feat_key = feature.lower().replace("feature", "f").replace("_", "")
                filtered_suite = unittest.TestSuite()
                for test in _flatten_suite(tier_suite):
                    test_name = test.id().lower()
                    if feat_key in test_name:
                        filtered_suite.addTest(test)
                suite.addTest(filtered_suite)
            else:
                suite.addTest(tier_suite)
        except Exception as e:
            print(f"[ERROR] Failed to load tier {t} module '{mod_name}': {e}", file=sys.stderr)
            
    return suite


def _flatten_suite(suite):
    """Recursively extracts individual test cases from nested TestSuites."""
    tests = []
    for item in suite:
        if isinstance(item, unittest.TestSuite):
            tests.extend(_flatten_suite(item))
        else:
            tests.append(item)
    return tests


class DetailedTestResult(unittest.TestResult):
    def __init__(self, stream=None, descriptions=None, verbosity=1):
        super().__init__(stream, descriptions, verbosity)
        self.verbosity = verbosity
        self.records = []
        self._current_start = 0

    def startTest(self, test):
        super().startTest(test)
        self._current_start = time.time()
        if self.verbosity > 1:
            desc = test.shortDescription() or str(test)
            print(f"  RUNNING: {test.id()} - {desc} ...", end=" ", flush=True)

    def addSuccess(self, test):
        super().addSuccess(test)
        elapsed = time.time() - self._current_start
        self.records.append({
            "test_id": test.id(),
            "description": test.shortDescription() or "",
            "status": "PASSED",
            "duration_sec": round(elapsed, 4),
            "error": None
        })
        if self.verbosity > 1:
            print(f"\033[92m[PASS]\033[0m ({elapsed:.3f}s)")

    def addFailure(self, test, err):
        super().addFailure(test, err)
        elapsed = time.time() - self._current_start
        err_msg = self._exc_info_to_string(err, test)
        self.records.append({
            "test_id": test.id(),
            "description": test.shortDescription() or "",
            "status": "FAILED",
            "duration_sec": round(elapsed, 4),
            "error": err_msg
        })
        if self.verbosity > 1:
            print(f"\033[91m[FAIL]\033[0m ({elapsed:.3f}s)")

    def addError(self, test, err):
        super().addError(test, err)
        elapsed = time.time() - self._current_start
        err_msg = self._exc_info_to_string(err, test)
        self.records.append({
            "test_id": test.id(),
            "description": test.shortDescription() or "",
            "status": "ERROR",
            "duration_sec": round(elapsed, 4),
            "error": err_msg
        })
        if self.verbosity > 1:
            print(f"\033[91m[ERROR]\033[0m ({elapsed:.3f}s)")

    def addSkip(self, test, reason):
        super().addSkip(test, reason)
        elapsed = time.time() - self._current_start
        self.records.append({
            "test_id": test.id(),
            "description": test.shortDescription() or "",
            "status": "SKIPPED",
            "duration_sec": round(elapsed, 4),
            "error": reason
        })
        if self.verbosity > 1:
            print(f"\033[93m[SKIP]\033[0m ({reason})")


def main():
    parser = argparse.ArgumentParser(description="The Lads Client - E2E Master Test Runner")
    parser.add_argument(
        "--tier", "-t",
        default="all",
        help="Test tiers to run: '1', '2', '3', '4', or comma-separated list '1,2' (default: all)"
    )
    parser.add_argument(
        "--feature", "-f",
        default=None,
        help="Filter tests by feature key, e.g. 'F1', 'F02', 'F11'"
    )
    parser.add_argument(
        "--verbose", "-v",
        action="store_true",
        help="Enable detailed per-test execution output"
    )
    parser.add_argument(
        "--json-output",
        default=None,
        help="Path to write JSON test execution results"
    )
    parser.add_argument(
        "--markdown-output",
        default=None,
        help="Path to write Markdown summary report"
    )
    
    args = parser.parse_args()
    
    # Parse tiers
    if args.tier.lower() == "all":
        selected_tiers = [1, 2, 3, 4]
    else:
        selected_tiers = [int(x.strip()) for x in args.tier.split(",") if x.strip().isdigit()]
        
    print("=" * 75)
    print(" The Lads Client - Opaque-Box E2E Test Suite Runner")
    print(f" Tiers: {selected_tiers} | Feature filter: {args.feature or 'ALL'}")
    print(f" Root:  {REPO_ROOT}")
    print("=" * 75)
    
    suite = load_tier_suites(selected_tiers, args.feature)
    test_count = suite.countTestCases()
    print(f"Discovered {test_count} test cases matching criteria.\n")
    
    start_time = time.time()
    result = DetailedTestResult(verbosity=2 if args.verbose else 1)
    suite.run(result)
    total_time = time.time() - start_time
    
    # Statistics
    passed = len([r for r in result.records if r["status"] == "PASSED"])
    failed = len(result.failures)
    errors = len(result.errors)
    skipped = len(result.skipped)
    total = len(result.records)
    
    print("\n" + "=" * 75)
    print(" E2E TEST EXECUTION SUMMARY")
    print("=" * 75)
    print(f" Total Tests Run: {total}")
    print(f" Passed:          \033[92m{passed}\033[0m")
    print(f" Failed:          \033[91m{failed}\033[0m" if failed else f" Failed:          {failed}")
    print(f" Errors:          \033[91m{errors}\033[0m" if errors else f" Errors:          {errors}")
    print(f" Skipped:         {skipped}")
    print(f" Elapsed Time:    {total_time:.2f} seconds")
    print("=" * 75)
    
    # Print failure details if any
    if failed > 0 or errors > 0:
        print("\n--- FAILURE & ERROR DETAILS ---")
        for f in result.failures:
            print(f"\n[FAIL] {f[0].id()}")
            print(f[1].strip())
        for e in result.errors:
            print(f"\n[ERROR] {e[0].id()}")
            print(e[1].strip())
            
    # Write JSON output if requested
    if args.json_output:
        json_data = {
            "timestamp": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
            "total": total,
            "passed": passed,
            "failed": failed,
            "errors": errors,
            "skipped": skipped,
            "elapsed_seconds": round(total_time, 2),
            "tiers": selected_tiers,
            "feature_filter": args.feature,
            "results": result.records
        }
        out_path = Path(args.json_output)
        out_path.parent.mkdir(parents=True, exist_ok=True)
        out_path.write_text(json.dumps(json_data, indent=2), encoding="utf-8")
        print(f"\nWrote JSON results to: {out_path}")
        
    # Write Markdown output if requested
    if args.markdown_output:
        md_lines = [
            "# E2E Test Suite Run Report",
            f"- **Timestamp**: {time.strftime('%Y-%m-%d %H:%M:%S UTC', time.gmtime())}",
            f"- **Tiers Tested**: {selected_tiers}",
            f"- **Total Tests**: {total}",
            f"- **Passed**: {passed}",
            f"- **Failed**: {failed}",
            f"- **Errors**: {errors}",
            f"- **Elapsed**: {total_time:.2f}s",
            "",
            "## Summary by Status",
            "| Status | Count |",
            "|---|---|",
            f"| Passed | {passed} |",
            f"| Failed | {failed} |",
            f"| Errors | {errors} |",
            f"| Skipped | {skipped} |",
            "",
            "## Test Results Catalog",
            "| Test ID | Status | Duration | Description |",
            "|---|---|---|---|"
        ]
        for r in result.records:
            md_lines.append(f"| `{r['test_id']}` | **{r['status']}** | {r['duration_sec']}s | {r['description']} |")
            
        out_path = Path(args.markdown_output)
        out_path.parent.mkdir(parents=True, exist_ok=True)
        out_path.write_text("\n".join(md_lines), encoding="utf-8")
        print(f"Wrote Markdown report to: {out_path}")
        
    # Exit with code 0 if no failures/errors, else 1
    sys.exit(0 if (failed == 0 and errors == 0) else 1)


if __name__ == "__main__":
    main()
