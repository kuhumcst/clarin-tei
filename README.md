CLARIN TEI reader
=================
This is a new synchronized facsimile and transcription reader for the TEI files on clarin.dk.

It is a fork of the Glossematics source code with many changes made to TEI styling, metadata retrieval and page structure fitting these TEI files, which are quite different from the ones at https://glossematics.dk.

Data preparation
----------------
I downloaded the "everyman" dataset from https://repository.clarin.dk/repository/xmlui/handle/20.500.12115/46 and extracted every zip file.

The extracted TIF files were recursively converted and renamed using the following commands (taken from https://github.com/kuhumcst/glossematics/issues/20):

```shell
find . -name '*.tif' -exec mogrify -format jpg -quality 70 {} +
find . -name '*.jpg' -exec rename 's/(?<!.tif).jpg/.tif.jpg/g' {} +
```

And to remove the remaining TIF files:

```shell
find . -name "*.tif" -type f -exec rm -f {} \;
```

To create thumbnails for search results:

```shell
mkdir thumbs
find . -name '*.jpg' -exec convert '{}' -resize 360x640 -set filename:newname "%t.%e" 'thumbs/thumb-%[filename:newname]' \;
```

Server setup
------------
The directory `/etc/clarin-tei` serves as the home directory of the system. The image and TEI files are to be found somewhere within the directory structure of `/etc/clarin-tei/files` while this Git repository is cloned at `/etc/clarin-tei/clarin-tei`.

The system requires Docker to run and is initialised as a `systemd` service:

```shell
cp system/clarin-tei.service /etc/systemd/system/clarin-tei.service
systemctl enable clarin-tei
systemctl start clarin-tei
```

Currently, this system requires a separate reverse proxy to be available on the public Internet.

For e.g. an nginx setup, the following snippet should be included:

```
location /tei {
	include proxy_params;
	proxy_pass http://127.0.0.1:6789/;
}
```
