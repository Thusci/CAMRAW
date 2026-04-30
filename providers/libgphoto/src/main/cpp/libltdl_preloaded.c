#include <ltdl.h>

#include "libltdl/lt_dlloader.h"

extern lt_dlvtable *dlopen_LTX_get_vtable(lt_user_data loader_data);

LT_DLSYM_CONST lt_dlsymlist lt_libltdl_LTX_preloaded_symbols[] = {
    { "libltdl", 0 },
    { "dlopen", 0 },
    { "get_vtable", (void *) dlopen_LTX_get_vtable },
    { 0, 0 }
};
