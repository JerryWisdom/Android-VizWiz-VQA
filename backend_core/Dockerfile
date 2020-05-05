FROM centos:7.6.1810
MAINTAINER hducsrao
WORKDIR /
ENV FRP_VERSION 0.27.0
RUN set -x && \
yum -y install wget && \
        wget --no-check-certificate https://github.com/fatedier/frp/releases/download/v${FRP_VERSION}/frp_${FRP_VERSION}_linux_amd64.tar.gz && \ 
        tar xzf frp_${FRP_VERSION}_linux_amd64.tar.gz && \
        mkdir /frp && \
	cd frp_${FRP_VERSION}_linux_amd64 && \
        mv frps /frp && \
	mv frps.ini /frp/frps.ini && \
        cd .. && \
        rm -f frp_${FRP_VERSION}_linux_amd64.tar.gz && \
        rm -rf frp_${FRP_VERSION}_linux_amd64

VOLUME /frp
CMD ./frp/frps -c /frp/frps.ini
