package com.wutsi.ndopify.platform.momo

import java.io.IOException

class MoMoException(val error: MoMoError, message: String) : IOException(message)
