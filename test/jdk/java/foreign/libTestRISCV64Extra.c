struct s_af2{
    float f[2];
};

struct s_ad2{
    double d[2];
};

struct s_ai1ad1{
    int a[1];
    double d[1];
};

struct s_s_lf{
    struct{long long l, float f} s;
};

struct s_af2 f_s_af2(struct s_af2 v){return v;}
struct s_ad2 f_s_ad2(struct s_ad2 v){return v;}
struct s_ai1ad1 f_s_ai1ad1(struct s_ai1ad1 v){return v;}
struct s_s_lf f_s_s_lf(struct s_s_lf v){return v;}