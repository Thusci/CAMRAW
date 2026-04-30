/* Android/aarch64 generated equivalent of libgphoto2's configure-time endian header. */
#ifndef GP_BYTEORDER_H
#define GP_BYTEORDER_H

#include <stdint.h>

#define swap16(x) ((uint16_t)(((uint16_t)(x) << 8) | ((uint16_t)(x) >> 8)))
#define swap32(x) ((uint32_t)((((uint32_t)(x) << 24) & 0xff000000UL) | \
                              (((uint32_t)(x) << 8) & 0x00ff0000UL) | \
                              (((uint32_t)(x) >> 8) & 0x0000ff00UL) | \
                              (((uint32_t)(x) >> 24) & 0x000000ffUL)))
#define swap64(x) ((uint64_t)((((uint64_t)(x) << 56) & 0xff00000000000000ULL) | \
                              (((uint64_t)(x) << 40) & 0x00ff000000000000ULL) | \
                              (((uint64_t)(x) << 24) & 0x0000ff0000000000ULL) | \
                              (((uint64_t)(x) << 8) & 0x000000ff00000000ULL) | \
                              (((uint64_t)(x) >> 8) & 0x00000000ff000000ULL) | \
                              (((uint64_t)(x) >> 24) & 0x0000000000ff0000ULL) | \
                              (((uint64_t)(x) >> 40) & 0x000000000000ff00ULL) | \
                              (((uint64_t)(x) >> 56) & 0x00000000000000ffULL)))

#ifndef htobe16
#define htobe16(x) swap16(x)
#endif
#ifndef htobe32
#define htobe32(x) swap32(x)
#endif
#ifndef htobe64
#define htobe64(x) swap64(x)
#endif
#ifndef be16toh
#define be16toh(x) htobe16(x)
#endif
#ifndef be32toh
#define be32toh(x) htobe32(x)
#endif
#ifndef be64toh
#define be64toh(x) htobe64(x)
#endif
#ifndef htole16
#define htole16(x) (x)
#endif
#ifndef htole32
#define htole32(x) (x)
#endif
#ifndef htole64
#define htole64(x) (x)
#endif
#ifndef le16toh
#define le16toh(x) (x)
#endif
#ifndef le32toh
#define le32toh(x) (x)
#endif
#ifndef le64toh
#define le64toh(x) (x)
#endif

#define HTOBE16(x) (x) = htobe16(x)
#define HTOBE32(x) (x) = htobe32(x)
#define HTOBE64(x) (x) = htobe64(x)
#define BE16TOH(x) (x) = be16toh(x)
#define BE32TOH(x) (x) = be32toh(x)
#define BE64TOH(x) (x) = be64toh(x)
#define HTOLE16(x) (void)(x)
#define HTOLE32(x) (void)(x)
#define HTOLE64(x) (void)(x)
#define LE16TOH(x) (void)(x)
#define LE32TOH(x) (void)(x)
#define LE64TOH(x) (void)(x)

#define be16atoh(x) ((uint16_t)(((x)[0] << 8) | (x)[1]))
#define be32atoh(x) ((uint32_t)(((x)[0] << 24) | ((x)[1] << 16) | ((x)[2] << 8) | (x)[3]))
#define be64atoh_x(x, off, shift) (((uint64_t)((x)[off])) << shift)
#define be64atoh(x) ((uint64_t)(be64atoh_x(x, 0, 56) | be64atoh_x(x, 1, 48) | \
                                be64atoh_x(x, 2, 40) | be64atoh_x(x, 3, 32) | \
                                be64atoh_x(x, 4, 24) | be64atoh_x(x, 5, 16) | \
                                be64atoh_x(x, 6, 8) | ((x)[7])))
#define le16atoh(x) ((uint16_t)(((x)[1] << 8) | (x)[0]))
#define le32atoh(x) ((uint32_t)(((x)[3] << 24) | ((x)[2] << 16) | ((x)[1] << 8) | (x)[0]))
#define le64atoh_x(x, off, shift) (((uint64_t)((x)[off])) << shift)
#define le64atoh(x) ((uint64_t)(le64atoh_x(x, 7, 56) | le64atoh_x(x, 6, 48) | \
                                le64atoh_x(x, 5, 40) | le64atoh_x(x, 4, 32) | \
                                le64atoh_x(x, 3, 24) | le64atoh_x(x, 2, 16) | \
                                le64atoh_x(x, 1, 8) | ((x)[0])))

#define htobe16a(a, x) (a)[0] = (uint8_t)((x) >> 8), (a)[1] = (uint8_t)(x)
#define htobe32a(a, x) (a)[0] = (uint8_t)((x) >> 24), (a)[1] = (uint8_t)((x) >> 16), \
                         (a)[2] = (uint8_t)((x) >> 8), (a)[3] = (uint8_t)(x)
#define htobe64a(a, x) (a)[0] = (uint8_t)((x) >> 56), (a)[1] = (uint8_t)((x) >> 48), \
                         (a)[2] = (uint8_t)((x) >> 40), (a)[3] = (uint8_t)((x) >> 32), \
                         (a)[4] = (uint8_t)((x) >> 24), (a)[5] = (uint8_t)((x) >> 16), \
                         (a)[6] = (uint8_t)((x) >> 8), (a)[7] = (uint8_t)(x)
#define htole16a(a, x) (a)[1] = (uint8_t)((x) >> 8), (a)[0] = (uint8_t)(x)
#define htole32a(a, x) (a)[3] = (uint8_t)((x) >> 24), (a)[2] = (uint8_t)((x) >> 16), \
                         (a)[1] = (uint8_t)((x) >> 8), (a)[0] = (uint8_t)(x)
#define htole64a(a, x) (a)[7] = (uint8_t)((x) >> 56), (a)[6] = (uint8_t)((x) >> 48), \
                         (a)[5] = (uint8_t)((x) >> 40), (a)[4] = (uint8_t)((x) >> 32), \
                         (a)[3] = (uint8_t)((x) >> 24), (a)[2] = (uint8_t)((x) >> 16), \
                         (a)[1] = (uint8_t)((x) >> 8), (a)[0] = (uint8_t)(x)

#endif
