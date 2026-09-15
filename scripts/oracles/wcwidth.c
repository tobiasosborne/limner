#define _XOPEN_SOURCE 700

#include <gnu/libc-version.h>
#include <locale.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <wchar.h>

int
main(int argc, char **argv)
{
    if (argc != 2) {
        fprintf(stderr, "usage: wcwidth OUTPUT.tsv\n");
        return 2;
    }

    const char *locale = setlocale(LC_CTYPE, "C.UTF-8");
    if (locale == NULL) {
        locale = setlocale(LC_CTYPE, "C.utf8");
    }
    if (locale == NULL || MB_CUR_MAX < 4) {
        fprintf(stderr, "a UTF-8 locale is required\n");
        return 2;
    }

    FILE *output = fopen(argv[1], "w");
    if (output == NULL) {
        perror("fopen");
        return 2;
    }

    fprintf(output, "# glibc=%s\n# locale=%s\n", gnu_get_libc_version(), locale);
    for (uint32_t code_point = 0; code_point <= 0x10FFFF; code_point++) {
        if (code_point < 0xD800 || code_point > 0xDFFF) {
            fprintf(output, "%06X\t%d\n", code_point,
                    wcwidth((wchar_t)code_point));
        }
    }

    if (fclose(output) != 0) {
        perror("fclose");
        return 2;
    }
    return 0;
}
