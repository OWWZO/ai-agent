import unittest

from server import resolve_worker_count


class ServerStartupTest(unittest.TestCase):
    def test_should_keep_multiple_workers_in_production_mode(self):
        self.assertEqual(
            3,
            resolve_worker_count(3, reload_enabled=False),
        )

    def test_should_force_single_worker_when_reload_is_enabled(self):
        self.assertEqual(
            1,
            resolve_worker_count(3, reload_enabled=True),
        )


if __name__ == "__main__":
    unittest.main()
