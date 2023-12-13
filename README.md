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
