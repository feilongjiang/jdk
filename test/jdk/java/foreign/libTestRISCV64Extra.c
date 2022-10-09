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
    struct{long long l; float f;} s;
};

struct s_ff {
    float a;
    float b __attribute__ ((aligned (8)));
};

struct __attribute__((__packed__)) s_fd {
  float a;
  double b;
};

struct s_ff2{
    float f7;
    float f8;
};

struct s_af2 f_s_af2(struct s_af2 v){return v;}
struct s_ad2 f_s_ad2(struct s_ad2 v){return v;}
struct s_ai1ad1 f_s_ai1ad1(struct s_ai1ad1 v){return v;}
struct s_s_lf f_s_s_lf(struct s_s_lf v){return v;}
struct s_ff f_s_ff(struct s_ff v) {return v;}
struct s_fd f_s_fd(struct s_fd v) {return v;}
struct s_ff2 f_spill(float f0, float f1, float f2, float f3, float f4, float f5, float f6, struct s_ff2 v) {return v;}
