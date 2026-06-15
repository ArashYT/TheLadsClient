import unittest
import sys
import os

# Add test path
sys.path.append(os.path.abspath("tests"))
import e2e_test_runner

class DebugRunner(unittest.TestCase):
    def test_run(self):
        # Instantiate test
        suite = unittest.TestSuite()
        suite.addTest(e2e_test_runner.TestTier1FeatureCoverage('test_f1_03_minecraft_version_filtering'))
        
        # We will run the test and print requests
        runner = unittest.TextTestRunner()
        result = runner.run(suite)
        
        print("\n--- RECORDED REQUESTS IN TEST RUNNER ---")
        with e2e_test_runner.recorded_requests_lock:
            print(f"Total requests: {len(e2e_test_runner.recorded_requests)}")
            for idx, r in enumerate(e2e_test_runner.recorded_requests):
                print(f"[{idx}] Path: {r['path']}")
                print(f"    Query: {r['query']}")
                print(f"    Headers: {r['headers']}")

if __name__ == '__main__':
    # Force mock API server to start if not started
    unittest.main()
