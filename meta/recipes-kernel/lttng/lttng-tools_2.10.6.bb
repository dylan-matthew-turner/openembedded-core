SECTION = "devel"
SUMMARY = "Linux Trace Toolkit Control"
DESCRIPTION = "The Linux trace toolkit is a suite of tools designed \
to extract program execution details from the Linux operating system \
and interpret them."

LICENSE = "GPLv2 & LGPLv2.1"
LIC_FILES_CHKSUM = "file://LICENSE;md5=01d7fc4496aacf37d90df90b90b0cac1 \
                    file://gpl-2.0.txt;md5=b234ee4d69f5fce4486a80fdaf4a4263 \
                    file://lgpl-2.1.txt;md5=0f0d71500e6a57fd24d825f33242b9ca"

DEPENDS = "liburcu popt libxml2 util-linux"
RDEPENDS_${PN} = "libgcc"
RDEPENDS_${PN}-ptest += "make perl bash gawk ${PN} babeltrace procps perl-module-overloading coreutils util-linux kmod lttng-modules"
RDEPENDS_${PN}-ptest_append_libc-glibc = " glibc-utils"
RDEPENDS_${PN}-ptest_append_libc-musl = " musl-utils"
# babelstats.pl wants getopt-long
RDEPENDS_${PN}-ptest += "perl-module-getopt-long"

PYTHON_OPTION = "am_cv_python_pyexecdir='${PYTHON_SITEPACKAGES_DIR}' \
                 am_cv_python_pythondir='${PYTHON_SITEPACKAGES_DIR}' \
                 PYTHON_INCLUDE='-I${STAGING_INCDIR}/python${PYTHON_BASEVERSION}${PYTHON_ABI}' \
"
PACKAGECONFIG ??= "lttng-ust"
PACKAGECONFIG[python] = "--enable-python-bindings ${PYTHON_OPTION},,python3 swig-native"
PACKAGECONFIG[lttng-ust] = "--with-lttng-ust, --without-lttng-ust, lttng-ust"
PACKAGECONFIG[kmod] = "--with-kmod, --without-kmod, kmod"
PACKAGECONFIG[manpages] = "--enable-man-pages, --disable-man-pages, asciidoc-native xmlto-native libxslt-native"
PACKAGECONFIG_remove_libc-musl = "lttng-ust"

SRC_URI = "https://lttng.org/files/lttng-tools/lttng-tools-${PV}.tar.bz2 \
           file://x32.patch \
           file://run-ptest \
           file://lttng-sessiond.service \
           file://0001-Fix-tests-link-libpause_consumer-on-liblttng-ctl.patch \
           file://0002-Fix-test-skip-test_getcpu_override-on-single-thread-.patch \
           file://0003-Fix-test-unit-the-tree-origin-can-be-a-symlink-itsel.patch \
           file://0004-Skip-when-testapp-is-not-present.patch\
           file://0005-Tests-use-modprobe-to-test-for-the-presence-of-lttng.patch \
           file://0006-Tests-check-for-lttng-modules-presence.patch \
           file://0007-Fix-getgrnam-is-not-MT-Safe-use-getgrnam_r.patch \
           file://0008-Fix-check-for-lttng-modules-presence-before-testing.patch \
           "

SRC_URI[md5sum] = "e88c521b5da6bb48a8187af633336ecc"
SRC_URI[sha256sum] = "f05df52bbebf8ce88d1b29e9e98cfc957d2ed738a345118018237ebdb581537c"

inherit autotools ptest pkgconfig useradd python3-dir manpages systemd

SYSTEMD_SERVICE_${PN} = "lttng-sessiond.service"
SYSTEMD_AUTO_ENABLE = "disable"

USERADD_PACKAGES = "${PN}"
GROUPADD_PARAM_${PN} = "tracing"

FILES_${PN} += "${libdir}/lttng/libexec/* ${datadir}/xml/lttng \
                ${PYTHON_SITEPACKAGES_DIR}/*"
FILES_${PN}-staticdev += "${PYTHON_SITEPACKAGES_DIR}/*.a"
FILES_${PN}-dev += "${PYTHON_SITEPACKAGES_DIR}/*.la"

# Since files are installed into ${libdir}/lttng/libexec we match 
# the libexec insane test so skip it.
# Python module needs to keep _lttng.so
INSANE_SKIP_${PN} = "libexec dev-so"
INSANE_SKIP_${PN}-dbg = "libexec"

do_install_append () {
    # install systemd unit file
    install -d ${D}${systemd_unitdir}/system
    install -m 0644 ${WORKDIR}/lttng-sessiond.service ${D}${systemd_unitdir}/system
}

do_install_ptest () {
    for f in Makefile tests/Makefile tests/utils/utils.sh tests/regression/tools/save-load/load-42*.lttng tests/regression/tools/save-load/configuration/load-42*.lttng ; do
        install -D "${B}/$f" "${D}${PTEST_PATH}/$f"
    done

    for f in config/tap-driver.sh config/test-driver src/common/config/session.xsd src/common/mi-lttng-3.0.xsd; do
        install -D "${S}/$f" "${D}${PTEST_PATH}/$f"
    done

    # Prevent 'make check' from recursing into non-test subdirectories.
    sed -i -e 's!^SUBDIRS = .*!SUBDIRS = tests!' "${D}${PTEST_PATH}/Makefile"

    # We don't need these
    sed -i -e '/dist_noinst_SCRIPTS = /,/^$/d' "${D}${PTEST_PATH}/tests/Makefile"

    # We shouldn't need to build anything in tests/utils
    sed -i -e 's!am__append_1 = . utils!am__append_1 = . !' \
        "${D}${PTEST_PATH}/tests/Makefile"

    # Copy the tests directory tree and the executables and
    # Makefiles found within.
    for d in $(find "${B}/tests" -type d -not -name .libs -printf '%P ') ; do
        install -d "${D}${PTEST_PATH}/tests/$d"
        find "${B}/tests/$d" -maxdepth 1 -executable -type f \
            -exec install -t "${D}${PTEST_PATH}/tests/$d" {} +
        # Take all .py scripts for tests using the python bindings.
        find "${B}/tests/$d" -maxdepth 1 -type f -name "*.py" \
            -exec install -t "${D}${PTEST_PATH}/tests/$d" {} +
        test -r "${B}/tests/$d/Makefile" && \
            install -t "${D}${PTEST_PATH}/tests/$d" "${B}/tests/$d/Makefile"
    done

    for d in $(find "${B}/tests" -type d -name .libs -printf '%P ') ; do
        for f in $(find "${B}/tests/$d" -maxdepth 1 -executable -type f -printf '%P ') ; do
            cp ${B}/tests/$d/$f ${D}${PTEST_PATH}/tests/`dirname $d`/$f
            case $f in
                *.so)
                    install -d ${D}${PTEST_PATH}/tests/$d/
                    ln -s  ../$f ${D}${PTEST_PATH}/tests/$d/$f
                    # Remove any rpath/runpath to pass QA check.
                    chrpath --delete ${D}${PTEST_PATH}/tests/$d/$f
                    ;;
            esac
        done
    done

    #
    # Use the versioned libs of liblttng-ust-dl.
    #
    ustdl="${D}${PTEST_PATH}/tests/regression/ust/ust-dl/test_ust-dl.py"
    if [ -e $ustdl ]; then
        sed -i -e 's!:liblttng-ust-dl.so!:liblttng-ust-dl.so.0!' $ustdl
    fi

    install ${B}/tests/unit/ini_config/sample.ini ${D}${PTEST_PATH}/tests/unit/ini_config/

    # We shouldn't need to build anything in tests/regression/tools
    sed -i -e 's!^SUBDIRS = tools !SUBDIRS = !' \
        "${D}${PTEST_PATH}/tests/regression/Makefile"

    # Prevent attempts to update Makefiles during test runs, and
    # silence "Making check in $SUBDIR" messages.
    find "${D}${PTEST_PATH}" -name Makefile -type f -exec \
        sed -i -e '/Makefile:/,/^$/d' -e '/%: %.in/,/^$/d' \
        -e '/echo "Making $$target in $$subdir"; \\/d' \
        -e 's/^srcdir = \(.*\)/srcdir = ./' \
        -e 's/^builddir = \(.*\)/builddir = ./' \
        -e 's/^all-am:.*/all-am:/' \
        {} +

    find "${D}${PTEST_PATH}" -name Makefile -type f -exec \
        touch -r "${B}/Makefile" {} +

    #
    # Need to stop generated binaries from rebuilding by removing their source dependencies
    #
    sed -e 's#\(^test.*OBJECTS.=\)#disable\1#g' \
        -e 's#\(^test.*DEPENDENCIES.=\)#disable\1#g' \
        -e 's#\(^test.*SOURCES.=\)#disable\1#g' \
        -e 's#\(^test.*LDADD.=\)#disable\1#g' \
        -i ${D}${PTEST_PATH}/tests/unit/Makefile

    # Substitute links to installed binaries.
    for prog in lttng lttng-relayd lttng-sessiond lttng-consumerd lttng-crash; do
        exedir="${D}${PTEST_PATH}/src/bin/${prog}"
        install -d "$exedir"
        case "$prog" in
            lttng-consumerd)
                ln -s "${libdir}/lttng/libexec/$prog" "$exedir"
                ;;
            *)
                ln -s "${bindir}/$prog" "$exedir"
                ;;
        esac
    done
}
