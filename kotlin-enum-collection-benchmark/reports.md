```
jvm summary:
Benchmark                                               Mode  Cnt    Score    Error  Units
JvmJdkEnumBenchmark.jdkEnumMapImmutableLikeCreate       avgt    8   53.426 ± 44.030  ns/op
JvmJdkEnumBenchmark.jdkEnumMapLargePutAll               avgt    8  277.765 ± 24.316  ns/op
JvmJdkEnumBenchmark.jdkEnumMapMutableCopy               avgt    8   17.544 ±  0.369  ns/op
JvmJdkEnumBenchmark.jdkEnumMapSmallPutGetRemove         avgt    8   89.658 ±  0.858  ns/op
JvmJdkEnumBenchmark.jdkEnumMapSnapshotCopy              avgt    8   20.270 ±  3.413  ns/op
JvmJdkEnumBenchmark.jdkEnumSetContainsAnyLarge          avgt    8    9.562 ±  3.905  ns/op
JvmJdkEnumBenchmark.jdkEnumSetDifferenceSmall           avgt    8    4.410 ±  0.654  ns/op
JvmJdkEnumBenchmark.jdkEnumSetIntersectSmall            avgt    8    3.913 ±  0.074  ns/op
JvmJdkEnumBenchmark.jdkEnumSetMutableCopy               avgt    8    4.718 ±  0.917  ns/op
JvmJdkEnumBenchmark.jdkEnumSetMutableMidAddRemove       avgt    8  120.865 ±  1.574  ns/op
JvmJdkEnumBenchmark.jdkEnumSetSnapshotCopy              avgt    8    3.701 ±  0.060  ns/op
JvmJdkEnumBenchmark.jdkEnumSetUnionSmall                avgt    8    3.681 ±  0.070  ns/op
KmpEnumCollectionBenchmark.enumMapImmutableCreate       avgt    8   25.857 ±  1.693  ns/op
KmpEnumCollectionBenchmark.enumMapLargePutAll           avgt    8  638.815 ±  5.859  ns/op
KmpEnumCollectionBenchmark.enumMapSmallPutGetRemove     avgt    8  122.189 ±  0.218  ns/op
KmpEnumCollectionBenchmark.enumMapToEnumMapSnapshot     avgt    8    5.204 ±  0.300  ns/op
KmpEnumCollectionBenchmark.enumMapToMutableEnumMapCopy  avgt    8    7.821 ±  0.297  ns/op
KmpEnumCollectionBenchmark.enumSetContainsAnyLarge      avgt    8    1.837 ±  0.015  ns/op
KmpEnumCollectionBenchmark.enumSetDifferenceSmall       avgt    8    1.718 ±  0.424  ns/op
KmpEnumCollectionBenchmark.enumSetIntersectSmall        avgt    8    1.626 ±  0.014  ns/op
KmpEnumCollectionBenchmark.enumSetMutableMidAddRemove   avgt    8  124.122 ±  4.665  ns/op
KmpEnumCollectionBenchmark.enumSetToEnumSetSnapshot     avgt    8    1.388 ±  0.017  ns/op
KmpEnumCollectionBenchmark.enumSetToMutableEnumSetCopy  avgt    8    1.321 ±  0.027  ns/op
KmpEnumCollectionBenchmark.enumSetUnionSmall            avgt    8    1.698 ±  0.467  ns/op

wasmJs summary:
Benchmark                                               Mode  Cnt     Score     Error  Units
KmpEnumCollectionBenchmark.enumMapImmutableCreate       avgt    8   101.073 ±  13.161  ns/op
KmpEnumCollectionBenchmark.enumMapLargePutAll           avgt    8  2432.776 ± 175.744  ns/op
KmpEnumCollectionBenchmark.enumMapSmallPutGetRemove     avgt    8   479.082 ±   1.985  ns/op
KmpEnumCollectionBenchmark.enumMapToEnumMapSnapshot     avgt    8    48.304 ±   2.990  ns/op
KmpEnumCollectionBenchmark.enumMapToMutableEnumMapCopy  avgt    8    58.467 ±   3.549  ns/op
KmpEnumCollectionBenchmark.enumSetContainsAnyLarge      avgt    8    11.689 ±   0.064  ns/op
KmpEnumCollectionBenchmark.enumSetDifferenceSmall       avgt    8    20.864 ±   0.409  ns/op
KmpEnumCollectionBenchmark.enumSetIntersectSmall        avgt    8    18.531 ±   1.010  ns/op
KmpEnumCollectionBenchmark.enumSetMutableMidAddRemove   avgt    8   263.605 ±   1.243  ns/op
KmpEnumCollectionBenchmark.enumSetToEnumSetSnapshot     avgt    8    21.714 ±   0.734  ns/op
KmpEnumCollectionBenchmark.enumSetToMutableEnumSetCopy  avgt    8    14.873 ±   0.600  ns/op
KmpEnumCollectionBenchmark.enumSetUnionSmall            avgt    8    16.128 ±   0.181  ns/op

mingwX64 summary:
Benchmark                                               Mode  Cnt     Score    Error  Units
KmpEnumCollectionBenchmark.enumMapImmutableCreate       avgt    8    75.234 ±  0.649  ns/op
KmpEnumCollectionBenchmark.enumMapLargePutAll           avgt    8  4543.758 ±  7.476  ns/op
KmpEnumCollectionBenchmark.enumMapSmallPutGetRemove     avgt    8  2943.010 ±  6.696  ns/op
KmpEnumCollectionBenchmark.enumMapToEnumMapSnapshot     avgt    8    45.341 ±  0.379  ns/op
KmpEnumCollectionBenchmark.enumMapToMutableEnumMapCopy  avgt    8    68.503 ±  0.521  ns/op
KmpEnumCollectionBenchmark.enumSetContainsAnyLarge      avgt    8    30.594 ±  0.206  ns/op
KmpEnumCollectionBenchmark.enumSetDifferenceSmall       avgt    8    43.707 ±  0.156  ns/op
KmpEnumCollectionBenchmark.enumSetIntersectSmall        avgt    8    43.792 ±  0.257  ns/op
KmpEnumCollectionBenchmark.enumSetMutableMidAddRemove   avgt    8  2658.437 ± 86.358  ns/op
KmpEnumCollectionBenchmark.enumSetToEnumSetSnapshot     avgt    8    25.991 ±  0.126  ns/op
KmpEnumCollectionBenchmark.enumSetToMutableEnumSetCopy  avgt    8    17.454 ±  0.037  ns/op
KmpEnumCollectionBenchmark.enumSetUnionSmall            avgt    8    43.044 ±  0.130  ns/op
```