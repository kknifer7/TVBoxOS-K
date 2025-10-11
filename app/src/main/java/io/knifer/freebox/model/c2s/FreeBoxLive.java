package io.knifer.freebox.model.c2s;


import java.util.Objects;

/**
 * 直播配置
 *
 * @author Knifer
 */
public class FreeBoxLive {

    private String name;

    private String url;

    private String ua;

    private String epg;

    private String logo;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUa() {
        return ua;
    }

    public void setUa(String ua) {
        this.ua = ua;
    }

    public String getEpg() {
        return epg;
    }

    public void setEpg(String epg) {
        this.epg = epg;
    }

    public String getLogo() {
        return logo;
    }

    public void setLogo(String logo) {
        this.logo = logo;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FreeBoxLive that = (FreeBoxLive) o;
        return Objects.equals(name, that.name) && Objects.equals(url, that.url) && Objects.equals(ua, that.ua) && Objects.equals(epg, that.epg) && Objects.equals(logo, that.logo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, url, ua, epg, logo);
    }

    @Override
    public String toString() {
        return "FreeBoxLive{" +
                "name='" + name + '\'' +
                ", url='" + url + '\'' +
                ", ua='" + ua + '\'' +
                ", epg='" + epg + '\'' +
                ", logo='" + logo + '\'' +
                '}';
    }

    public static FreeBoxLive from(String url) {
        FreeBoxLive result = new FreeBoxLive();

        result.setUrl(url);

        return result;
    }
}
