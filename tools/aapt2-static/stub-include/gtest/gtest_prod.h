// Minimal replacement for googletest's gtest_prod.h (only FRIEND_TEST is
// needed by libziparchive headers; tests are not built).
#pragma once
#define FRIEND_TEST(test_case_name, test_name) \
  friend class test_case_name##_##test_name##_Test
