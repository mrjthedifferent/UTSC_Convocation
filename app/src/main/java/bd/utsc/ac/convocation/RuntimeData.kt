package bd.utsc.ac.convocation

class RuntimeData {
    companion object {
        //permissions
        val permissions = arrayOf(
            "android.permission.INTERNET",
            "android.permission.ACCESS_NETWORK_STATE",
            "android.permission.CAMERA",
        )

        val systemEnv = Environment.STAGING
        var env = Environment.STAGING

        fun getURL(env: Environment): String {
            return when (env) {
                Environment.PRODUCTION -> "https://convocation.ustc.ac.bd/app/"
                Environment.STAGING -> "https://convocation.ustc.yasinuddowla.com/app/"
            }
        }
    }
}