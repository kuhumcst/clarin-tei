FROM ubuntu:24.04

RUN apt-get clean && \
    apt-get update && \
    DEBIAN_FRONTEND=noninteractive \
    apt-get install --no-install-recommends -y \
    git \
    npm \
    default-jdk \
    curl \
    rlwrap \
    locales \
    sed

# Fix emojis not showing up on HTML pages
# https://medium.com/quiq-blog/handling-emoticons-or-other-unicode-characters-beware-of-docker-images-f0f11673dac4
RUN locale-gen en_US.UTF-8
ENV LANG en_US.UTF-8
ENV LANGUAGE en_US:en
ENV LC_ALL en_US.UTF-8

# The recommended way of installing Clojure CLI tools
# https://clojure.org/guides/getting_started#_installation_on_linux
RUN curl -L -O https://github.com/clojure/brew-install/releases/latest/download/linux-install.sh
RUN chmod +x linux-install.sh
RUN ./linux-install.sh

WORKDIR /etc/clarin-tei

# Install JS dependencies
RUN npm init -y
RUN npm install -g shadow-cljs
RUN npm install \
    shadow-cljs \
    react@18.3.1 \
    react-dom@18.3.1 \
    create-react-class

# Fetch backend dependencies
COPY ./deps.edn /etc/clarin-tei/deps.edn
RUN clojure -A:backend -Spath

# Fetch frontend dependencies
COPY ./shadow-cljs.edn /etc/clarin-tei/shadow-cljs.edn
RUN shadow-cljs --aliases :frontend classpath

# Prepare frontend app source files
COPY ./src /etc/clarin-tei/src
COPY ./resources /etc/clarin-tei/resources

# the CSS files are patched up beforehand to match the "/clarin" proxy-prefix
WORKDIR /etc/clarin-tei/resources/dk/clarin/tei/public/css
RUN sed -i 's!/images!/clarin/images!g' *.css
WORKDIR /etc/clarin-tei

#  Create a release build of the frontend app
RUN shadow-cljs --aliases :frontend release app

# Build an uberjar for the backend
RUN clojure -T:build org.corfield.build/uber :lib dk.clarin/tei :main dk.clarin.tei.backend :uber-file "\"clarin-tei.jar\""

# Add the conf
COPY ./docker/clarin-tei/conf.edn /etc/clarin-tei/conf.edn

# Prepare the start script
COPY ./docker/clarin-tei/start.sh /etc/clarin-tei/start.sh
RUN chmod +x /etc/clarin-tei/start.sh

# Switch to a non-root user
# NOTE: the ownership of the volumes must be set to group id 1024 on the host!
# https://medium.com/@nielssj/docker-volumes-and-file-system-permissions-772c1aee23ca
RUN addgroup --gid 1024 clarin-tei
RUN adduser --disabled-password --gecos "" --force-badname --ingroup clarin-tei clarin-tei-user
USER clarin-tei-user

# Run the backend web service
ENTRYPOINT ["./start.sh"]
