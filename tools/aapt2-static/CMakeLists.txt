# Standalone CMake build of AOSP aapt2 as a static Android executable.
# Configure with the NDK toolchain file; see build.sh.
cmake_minimum_required(VERSION 3.20)
project(aapt2_static C CXX ASM)

if(NOT DEFINED AOSP_SRC)
  message(FATAL_ERROR "AOSP_SRC not set")
endif()
if(NOT DEFINED HOST_PROTOC)
  message(FATAL_ERROR "HOST_PROTOC not set")
endif()
set(AAPT2_BUILD_NUMBER "android-16.0.0_r4" CACHE STRING "string reported by 'aapt2 version'")

set(S ${AOSP_SRC})
set(FWB ${S}/frameworks/base)
set(CMAKE_POSITION_INDEPENDENT_CODE OFF)

# Common: we build "host tool" flavoured code that happens to run on bionic.
add_compile_definitions(AAPT2_STATIC_TOOL=1 _FILE_OFFSET_BITS=64)
add_compile_options(-Wno-unknown-warning-option -ffunction-sections -fdata-sections)
# The binary is linked against the NDK's static libc.a, which always contains
# the full (latest) bionic, so APIs newer than the minSdk are present at link
# time.  Expose their declarations (fdsan, __system_property_read_callback,
# liblog's own API-30 functions) instead of hiding them behind API guards.
add_compile_definitions(__ANDROID_UNAVAILABLE_SYMBOLS_ARE_WEAK__)
add_compile_options(-Wno-unguarded-availability -Wno-unguarded-availability-new)

set(INC_LIBBASE   ${S}/system/libbase/include)
set(INC_FMT       ${S}/external/fmtlib/include)
set(INC_LIBLOG    ${S}/system/logging/liblog/include)
set(INC_CUTILS    ${S}/system/core/libcutils/include)
set(INC_UTILS     ${S}/system/core/libutils/include ${S}/system/core/libutils/binder/include)
set(INC_SYSTEM    ${S}/system/core/libsystem/include)
set(INC_ZIP       ${S}/system/libziparchive/include)
set(INC_INCFSUTIL ${S}/system/incremental_delivery/incfs/util/include)
set(INC_ZLIB      ${S}/external/zlib)
set(INC_PNG       ${S}/external/libpng)
set(INC_EXPAT     ${S}/external/expat/expat/lib)
set(INC_PROTOBUF  ${S}/external/protobuf/src)
set(INC_ANDROIDFW ${FWB}/libs/androidfw/include ${FWB}/libs/androidfw/include_pathutils)

set(COMMON_INCS ${INC_LIBBASE} ${INC_FMT} ${INC_LIBLOG} ${INC_CUTILS} ${INC_UTILS} ${INC_SYSTEM})

# ---------------------------------------------------------------- zlib
set(Z ${S}/external/zlib)
add_library(z STATIC
  ${Z}/adler32.c ${Z}/adler32_simd.c ${Z}/compress.c ${Z}/cpu_features.c ${Z}/crc32.c
  ${Z}/crc32_simd.c ${Z}/crc_folding.c ${Z}/deflate.c ${Z}/gzclose.c ${Z}/gzlib.c
  ${Z}/gzread.c ${Z}/gzwrite.c ${Z}/infback.c ${Z}/inffast.c ${Z}/inflate.c
  ${Z}/inftrees.c ${Z}/trees.c ${Z}/uncompr.c ${Z}/zutil.c)
target_include_directories(z PUBLIC ${Z})
target_compile_definitions(z PRIVATE HAVE_HIDDEN ZLIB_CONST CHROMIUM_ZLIB_NO_CASTAGNOLI INFLATE_CHUNK_READ_64LE)
target_compile_options(z PRIVATE -O3 -Wno-deprecated-non-prototype -Wno-unused -Wno-unused-parameter)
if(ANDROID_ABI STREQUAL "arm64-v8a")
  target_compile_definitions(z PRIVATE ADLER32_SIMD_NEON CRC32_ARMV8_CRC32 ARMV8_OS_LINUX)
elseif(ANDROID_ABI STREQUAL "x86_64")
  target_compile_definitions(z PRIVATE X86_NOT_WINDOWS ADLER32_SIMD_SSSE3)
endif()

# ---------------------------------------------------------------- libpng
set(P ${S}/external/libpng)
set(PNG_SRCS png.c pngerror.c pngget.c pngmem.c pngpread.c pngread.c pngrio.c pngrtran.c
  pngrutil.c pngset.c pngtrans.c pngwio.c pngwrite.c pngwtran.c pngwutil.c)
list(TRANSFORM PNG_SRCS PREPEND ${P}/)
# NEON / SSE2 filters for every ABI (the NDK enables NEON for armeabi-v7a and SSSE3 for x86 by default)
if(ANDROID_ABI STREQUAL "arm64-v8a" OR ANDROID_ABI STREQUAL "armeabi-v7a")
  list(APPEND PNG_SRCS ${P}/arm/arm_init.c ${P}/arm/filter_neon_intrinsics.c ${P}/arm/palette_neon_intrinsics.c)
elseif(ANDROID_ABI STREQUAL "x86_64" OR ANDROID_ABI STREQUAL "x86")
  list(APPEND PNG_SRCS ${P}/intel/intel_init.c ${P}/intel/filter_sse2_intrinsics.c)
endif()
add_library(png STATIC ${PNG_SRCS})
target_include_directories(png PUBLIC ${P})
target_compile_options(png PRIVATE -std=gnu89 -O3 -Wno-unused-parameter)
if(ANDROID_ABI STREQUAL "x86_64" OR ANDROID_ABI STREQUAL "x86")
  target_compile_definitions(png PRIVATE PNG_INTEL_SSE_OPT=1)
endif()
target_link_libraries(png PUBLIC z)

# ---------------------------------------------------------------- expat
set(E ${S}/external/expat)
add_library(expat STATIC ${E}/expat/lib/xmlparse.c ${E}/expat/lib/xmlrole.c ${E}/expat/lib/xmltok.c)
target_include_directories(expat PUBLIC ${INC_EXPAT} PRIVATE ${E})
target_compile_definitions(expat PRIVATE HAVE_EXPAT_CONFIG_H)
target_compile_options(expat PRIVATE -fno-strict-aliasing -include stdio.h -UWIN32_LEAN_AND_MEAN)

# ---------------------------------------------------------------- fmtlib
add_library(fmt STATIC ${S}/external/fmtlib/src/format.cc)
target_include_directories(fmt PUBLIC ${INC_FMT})
target_compile_options(fmt PRIVATE -fno-exceptions)

# ---------------------------------------------------------------- liblog
set(L ${S}/system/logging/liblog)
add_library(log STATIC
  ${L}/log_event_list.cpp ${L}/log_event_write.cpp ${L}/logger_name.cpp ${L}/logger_read.cpp
  ${L}/logger_write.cpp ${L}/logprint.cpp ${L}/properties.cpp ${L}/event_tag_map.cpp
  ${L}/log_time.cpp ${L}/pmsg_reader.cpp ${L}/pmsg_writer.cpp ${L}/logd_reader.cpp ${L}/logd_writer.cpp)
target_include_directories(log PUBLIC ${INC_LIBLOG} PRIVATE ${COMMON_INCS})
target_compile_definitions(log PRIVATE LIBLOG_LOG_TAG=1006 SNET_EVENT_LOG_TAG=1397638484 ANDROID_DEBUGGABLE=0)
target_compile_options(log PRIVATE -Wno-unused-parameter)
set_target_properties(log PROPERTIES CXX_STANDARD 17)

# ---------------------------------------------------------------- libbase
set(B ${S}/system/libbase)
add_library(base STATIC
  ${B}/chrono_utils.cpp ${B}/cmsg.cpp ${B}/file.cpp ${B}/hex.cpp
  ${B}/logging.cpp ${B}/mapped_file.cpp ${B}/parsebool.cpp ${B}/parsenetaddress.cpp
  ${B}/posix_strerror_r.cpp ${B}/process.cpp ${B}/properties.cpp ${B}/stringprintf.cpp
  ${B}/strings.cpp ${B}/threads.cpp ${B}/errors_unix.cpp)
target_include_directories(base PUBLIC ${INC_LIBBASE} ${INC_FMT} PRIVATE ${INC_LIBLOG})
target_link_libraries(base PUBLIC fmt log)
set_target_properties(base PROPERTIES CXX_STANDARD 20)

# ---------------------------------------------------------------- libcutils
set(C ${S}/system/core/libcutils)
add_library(cutils STATIC
  ${C}/config_utils.cpp ${C}/load_file.cpp ${C}/native_handle.cpp ${C}/properties.cpp
  ${C}/record_stream.cpp ${C}/strlcpy.c ${C}/fs.cpp ${C}/hashmap.cpp ${C}/multiuser.cpp
  ${C}/str_parms.cpp ${C}/ashmem-host.cpp ${C}/trace-host.cpp)
target_include_directories(cutils PUBLIC ${INC_CUTILS} ${INC_SYSTEM} PRIVATE ${INC_LIBBASE} ${INC_LIBLOG} ${INC_UTILS})
target_link_libraries(cutils PUBLIC log base)
set_target_properties(cutils PROPERTIES CXX_STANDARD 20)

# ---------------------------------------------------------------- libutils
set(U ${S}/system/core/libutils)
add_library(utils STATIC
  ${U}/FileMap.cpp ${U}/JenkinsHash.cpp ${U}/LightRefBase.cpp ${U}/NativeHandle.cpp
  ${U}/Printer.cpp ${U}/StopWatch.cpp ${U}/SystemClock.cpp ${U}/Timers.cpp ${U}/Tokenizer.cpp
  ${U}/misc.cpp
  ${U}/binder/Errors.cpp ${U}/binder/RefBase.cpp ${U}/binder/SharedBuffer.cpp
  ${U}/binder/String16.cpp ${U}/binder/String8.cpp ${U}/binder/StrongPointer.cpp
  ${U}/binder/Unicode.cpp ${U}/binder/VectorImpl.cpp)
target_include_directories(utils PUBLIC ${INC_UTILS} PRIVATE ${INC_LIBBASE} ${INC_LIBLOG} ${INC_CUTILS} ${INC_SYSTEM})
target_compile_definitions(utils PRIVATE ANDROID_UTILS_REF_BASE_DISABLE_IMPLICIT_CONSTRUCTION)
target_link_libraries(utils PUBLIC cutils log base)
set_target_properties(utils PROPERTIES CXX_STANDARD 20)

# ---------------------------------------------------------------- libziparchive
set(ZA ${S}/system/libziparchive)
add_library(ziparchive STATIC
  ${ZA}/zip_archive.cc ${ZA}/zip_archive_stream_entry.cc ${ZA}/zip_cd_entry_map.cc
  ${ZA}/zip_error.cpp ${ZA}/zip_writer.cc)
target_include_directories(ziparchive PUBLIC ${INC_ZIP} ${CMAKE_CURRENT_SOURCE_DIR}/stub-include PRIVATE ${ZA}/incfs_support/include ${INC_LIBBASE} ${INC_LIBLOG} ${INC_UTILS} ${INC_CUTILS})
target_compile_definitions(ziparchive PRIVATE ZLIB_CONST INCFS_SUPPORT_DISABLED=1)
target_link_libraries(ziparchive PUBLIC base log z)
set_target_properties(ziparchive PROPERTIES CXX_STANDARD 20)

# ---------------------------------------------------------------- libincfs-utils
add_library(incfs_utils STATIC ${S}/system/incremental_delivery/incfs/util/map_ptr.cpp)
target_include_directories(incfs_utils PUBLIC ${INC_INCFSUTIL} PRIVATE ${INC_LIBBASE} ${INC_UTILS} ${INC_LIBLOG} ${INC_CUTILS})
target_link_libraries(incfs_utils PUBLIC base utils)
set_target_properties(incfs_utils PROPERTIES CXX_STANDARD 20)

# ---------------------------------------------------------------- abseil + libprotobuf (protobuf 25.x, android-16)
# protobuf 22+ depends on abseil-cpp; both come from the same AOSP tag and are built with their own CMake files.
set(ABSL_PROPAGATE_CXX_STD ON CACHE BOOL "" FORCE)
set(ABSL_BUILD_TESTING OFF CACHE BOOL "" FORCE)
set(ABSL_ENABLE_INSTALL OFF CACHE BOOL "" FORCE)
set(protobuf_ABSL_PROVIDER "module" CACHE STRING "" FORCE)
set(ABSL_ROOT_DIR ${S}/external/abseil-cpp CACHE PATH "" FORCE)
set(protobuf_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(protobuf_BUILD_CONFORMANCE OFF CACHE BOOL "" FORCE)
set(protobuf_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(protobuf_BUILD_PROTOC_BINARIES OFF CACHE BOOL "" FORCE)
set(protobuf_BUILD_LIBPROTOC OFF CACHE BOOL "" FORCE)
set(protobuf_BUILD_LIBUPB OFF CACHE BOOL "" FORCE)
set(protobuf_INSTALL OFF CACHE BOOL "" FORCE)
set(protobuf_WITH_ZLIB OFF CACHE BOOL "" FORCE)
set(protobuf_BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(protobuf_DISABLE_RTTI OFF CACHE BOOL "" FORCE)
set(CMAKE_CXX_STANDARD 17)
add_subdirectory(${S}/external/protobuf ${CMAKE_CURRENT_BINARY_DIR}/protobuf EXCLUDE_FROM_ALL)
set(CMAKE_CXX_STANDARD)
add_library(protobuf ALIAS libprotobuf)

# ---------------------------------------------------------------- libandroidfw (host flavour)
set(AFW ${FWB}/libs/androidfw)
set(AFW_SRCS ApkAssets.cpp ApkParsing.cpp Asset.cpp AssetDir.cpp AssetManager.cpp AssetManager2.cpp
  AssetsProvider.cpp AttributeResolution.cpp BigBuffer.cpp BigBufferStream.cpp ChunkIterator.cpp
  ConfigDescription.cpp FileStream.cpp Idmap.cpp LoadedArsc.cpp Locale.cpp LocaleData.cpp misc.cpp
  NinePatch.cpp ObbFile.cpp PosixUtils.cpp Png.cpp PngChunkFilter.cpp PngCrunch.cpp ResourceTimer.cpp
  ResourceTypes.cpp ResourceUtils.cpp StreamingZipInflater.cpp StringPool.cpp TypeWrappers.cpp
  Util.cpp ZipFileRO.cpp ZipUtils.cpp PathUtils.cpp LocaleDataLookup.cpp)
list(TRANSFORM AFW_SRCS PREPEND ${AFW}/)
add_library(androidfw STATIC ${AFW_SRCS})
target_include_directories(androidfw PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}/stub-include ${INC_ANDROIDFW} ${INC_INCFSUTIL} ${COMMON_INCS} ${INC_ZIP} ${INC_PNG} ${INC_ZLIB})
target_compile_definitions(androidfw PRIVATE STATIC_ANDROIDFW_FOR_TOOLS)
target_compile_options(androidfw PRIVATE -Wno-unused-parameter)
target_link_libraries(androidfw PUBLIC incfs_utils ziparchive utils cutils base log png z)
set_target_properties(androidfw PROPERTIES CXX_STANDARD 23 CXX_EXTENSIONS ON)

# ---------------------------------------------------------------- libbuildversion (fixed build number)
set(BV_CPP ${CMAKE_CURRENT_BINARY_DIR}/buildversion.cpp)
file(WRITE ${BV_CPP}
"#include <build/version.h>\nnamespace android { namespace build {\nstd::string GetBuildNumber() { return \"${AAPT2_BUILD_NUMBER}\"; }\n} }\n")
add_library(buildversion STATIC ${BV_CPP})
target_include_directories(buildversion PUBLIC ${S}/build/soong/cc/libbuildversion/include)

# ---------------------------------------------------------------- aapt2 protos
set(A ${FWB}/tools/aapt2)
set(PROTO_OUT ${CMAKE_CURRENT_BINARY_DIR}/proto)
set(PROTO_DIR ${PROTO_OUT}/frameworks/base/tools/aapt2)
file(MAKE_DIRECTORY ${PROTO_DIR})
set(PROTO_SRCS)
foreach(p ApkInfo Configuration Resources ResourceMetadata ResourcesInternal)
  list(APPEND PROTO_SRCS ${PROTO_DIR}/${p}.pb.cc)
  add_custom_command(OUTPUT ${PROTO_DIR}/${p}.pb.cc ${PROTO_DIR}/${p}.pb.h
    COMMAND ${HOST_PROTOC} --proto_path=${S} --cpp_out=${PROTO_OUT} frameworks/base/tools/aapt2/${p}.proto
    WORKING_DIRECTORY ${S}
    DEPENDS ${A}/${p}.proto ${HOST_PROTOC} VERBATIM)
endforeach()

# ---------------------------------------------------------------- aapt2
set(AAPT2_SRCS
  compile/IdAssigner.cpp compile/InlineXmlFormatParser.cpp compile/PseudolocaleGenerator.cpp
  compile/Pseudolocalizer.cpp compile/XmlIdCollector.cpp configuration/ConfigurationParser.cpp
  dump/DumpManifest.cpp filter/AbiFilter.cpp filter/ConfigFilter.cpp format/Archive.cpp
  format/Container.cpp format/binary/BinaryResourceParser.cpp format/binary/ResChunkPullParser.cpp
  format/binary/ResEntryWriter.cpp format/binary/TableFlattener.cpp format/binary/XmlFlattener.cpp
  format/proto/ProtoDeserialize.cpp format/proto/ProtoSerialize.cpp io/File.cpp io/FileSystem.cpp
  io/StringStream.cpp io/Util.cpp io/ZipArchive.cpp link/AutoVersioner.cpp
  link/FeatureFlagsFilter.cpp link/FlaggedXmlVersioner.cpp link/FlagNotEnabledResourceRemover.cpp link/ManifestFixer.cpp link/NoDefaultResourceRemover.cpp
  link/PrivateAttributeMover.cpp link/ReferenceLinker.cpp link/ResourceExcluder.cpp
  link/TableMerger.cpp link/XmlCompatVersioner.cpp link/XmlNamespaceRemover.cpp
  link/XmlReferenceLinker.cpp optimize/MultiApkGenerator.cpp optimize/ResourceDeduper.cpp
  optimize/ResourceFilter.cpp optimize/Obfuscator.cpp optimize/VersionCollapser.cpp
  process/ProductFilter.cpp process/SymbolTable.cpp split/TableSplitter.cpp text/Printer.cpp
  text/Unicode.cpp text/Utf8Iterator.cpp util/Files.cpp util/Util.cpp Debug.cpp DominatorTree.cpp
  java/AnnotationProcessor.cpp java/ClassDefinition.cpp java/JavaClassGenerator.cpp
  java/ManifestClassGenerator.cpp java/ProguardRules.cpp LoadedApk.cpp Resource.cpp
  ResourceParser.cpp ResourceTable.cpp ResourceUtils.cpp ResourceValues.cpp SdkConstants.cpp
  trace/TraceBuffer.cpp xml/XmlActionExecutor.cpp xml/XmlDom.cpp xml/XmlPullParser.cpp
  xml/XmlUtil.cpp ValueTransformer.cpp
  Main.cpp cmd/ApkInfo.cpp cmd/Command.cpp cmd/Compile.cpp cmd/Convert.cpp cmd/Diff.cpp
  cmd/Dump.cpp cmd/Link.cpp cmd/Optimize.cpp cmd/Util.cpp)
list(TRANSFORM AAPT2_SRCS PREPEND ${A}/)
add_executable(aapt2 ${AAPT2_SRCS} ${PROTO_SRCS})
target_include_directories(aapt2 PRIVATE ${A} ${PROTO_OUT} ${PROTO_DIR} ${FWB}/cmds/idmap2/libidmap2_policies/include ${INC_EXPAT})
target_compile_options(aapt2 PRIVATE -Wall -Wno-unused-parameter -Wno-missing-field-initializers
  -fno-exceptions -fno-rtti -Wno-deprecated-declarations)
set_target_properties(aapt2 PROPERTIES CXX_STANDARD 23 CXX_EXTENSIONS ON)
target_link_libraries(aapt2 PRIVATE androidfw protobuf expat buildversion incfs_utils ziparchive
  utils cutils base fmt log png z)
# abseil's log sink links "-llog" on Android: with -static that must resolve to our static liblog.a (built above)
target_link_directories(aapt2 PRIVATE ${CMAKE_CURRENT_BINARY_DIR})
target_link_options(aapt2 PRIVATE -static -Wl,--gc-sections -Wl,-z,max-page-size=16384)
