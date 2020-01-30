# Ada Installation Guide (MacOS) - Version 0.8.1

(Expected time: 30-45 mins)

&nbsp;

### 0. Preparation

Recommended OS: *MacOS* 10.14.4

Recommended resources:

* **Ada Server**: 8 GB RAM, 8 CPUs, 20 GB disc space
* **Mongo DB**: 8 GB RAM, 4 CPUs, 50 GB disc space
* **Elastic Search DB**: 8 GB RAM, 4 CPUs, 50 GB disc space

&nbsp; 

### 1. **Java** 1.8

```sh
brew cask install java8
```

&nbsp;

### 2. **Mongo** DB

* Install MongoDB (4.0.10)
(Note that Ada is compatible with *any* 3.2, 3.4, 3.6, and 4.0 relaease of Mongo in case you fail to install the recommended version)

```sh
curl -L -O https://fastdl.mongodb.org/osx/mongodb-osx-ssl-x86_64-4.0.10.tgz
tar -xvf mongodb-osx-ssl-x86_64-4.0.10.tgz
mv mongodb-osx-x86_64-4.0.10 mongodb
mkdir -p /data/db
sudo chown -R `id -un` /data/db
```

* Configure memory and other settings in `/usr/local/etc/mongod.conf`
(set a reasonable `cacheSizeGB`, recommended to 50% of available RAM, [ref](https://docs.mongodb.com/v4.0/reference/configuration-options/#storage.wiredTiger.engineConfig.cacheSizeGB))

```sh
  ...
  wiredTiger:
    engineConfig:
      cacheSizeGB: 5
      journalCompressor: none
    collectionConfig:
      blockCompressor: snappy
```

* Create a limits file `/etc/security/limits.d/mongodb.conf`

```sh
mongodb    soft    nofile          1625538
mongodb    hard    nofile          1625538
mongodb    soft    nopro           64000
mongodb    hard    nopro           64000
mongodb    soft    memlock         unlimited
mongodb    hard    memlock         unlimited
mongodb    soft    fsize           unlimited
mongodb    hard    fsize           unlimited
mongodb    soft    cpu             unlimited
mongodb    hard    cpu             unlimited
mongodb    soft    as              unlimited
mongodb    hard    as              unlimited
```
* To force your new limits to be loaded log out of all your current sessions and log back in.

* Add MongoDB installation folder to PATH environment variable `/etc/paths`

```
<mongo_installation_folder>/bin
```

* Start Mongo

```sh
mongod
```

* To check  if everything works as expected see the log file `/usr/local/var/log/mongodb`.

* *Recommendation*: For convenient DB exploration and query execution install [Robomongo (Robo3T)](https://robomongo.org/download) UI client.

* Optionally set up users with authentication as described [here](https://docs.mongodb.com/v4.0/tutorial/create-users/).

* For tuning tips go to [here](https://docs.mongodb.com/v4.0/administration/analyzing-mongodb-performance/).

&nbsp; 

## 3. **Elastic Search**

* Install ES (5.6.10)

```sh
curl -L -O https://artifacts.elastic.co/downloads/elasticsearch/elasticsearch-5.6.10.tar.gz
tar -xvf elasticsearch-5.6.10.tar.gz
mv elasticsearch-5.6.10 elasticsearch
```

* Modify the configuration in `<es_installation_folder>/config/elasticsearch.yml`

```sh
  cluster.name: ada-cluster       (if not changed "elasticsearch" is used by default)
  bootstrap.memory_lock: true
  network.host: x.x.x.x           (set to a non-localhost ip address if the db should be accessible within a network)
  thread_pool:
      index:
          queue_size: 8000
      search:
          queue_size: 8000 
      bulk:
          queue_size: 500
  indices.query.bool.max_clause_count: 4096
```

* If you want to store data in a special directory set also
 
```
path.data: /your_custom_path
```
(Note that you need to make `your_custom_path` writeable for the `elasticsearch` user)

* Create a limits file `/etc/security/limits.d/elasticsearch.conf`

```sh
elasticsearch    soft    nofile          1625538
elasticsearch    hard    nofile          1625538
elasticsearch    soft    memlock         unlimited
elasticsearch    hard    memlock         unlimited
```

* To force your new limits to be loaded log out of all your current sessions and log back in.

* Configure open-file and locked memory constraints in `/etc/default/elasticsearch`
```
MAX_OPEN_FILES=1625538
MAX_LOCKED_MEMORY=unlimited
```
* Set a reasonable heap size; recommended to 50% of available RAM, but no more than 31g in `<es_installation_folder>/elasticsearch/config/jvm.options`, e.g.
```
-Xms5g
-Xmx5g
```

* Add Elastic Search to PATH environment variable `/etc/paths`

~~~
<es_installation_folder>/bin
~~~

* Start ES
```
elasticsearch
```

* To check if everything works as expected see the log file(s) at `/usr/local/var/log/elasticsearch` and/or curl the server info by `curl -XGET localhost:9200`.

* *Recommendation*: For convenient DB exploration and query execution you might want to install the *cerebro* app:

```sh
curl -L -O https://github.com/lmenezes/cerebro/releases/download/v0.8.3/cerebro-0.8.3.zip
tar -xvf cerebro-0.8.3.zip
```
* Open the configuration file `conf/application.conf` and add the host and name of your Elastic server to the `hosts` section, e.g.,
```
  {
    host = "http://x.x.x.x:9200"
    name = "ada-cluster"
  }
```
* Run Cerebro, for example at the port 9209
```
 sudo ./bin/cerebro -Dhttp.port=9209
```
(Cerebro web client is then accessible at [http://localhost:9209](http://localhost:9209)) 

* Alternatively you can install *Kibana*, which also allows execution of Elastic queries and many more, as described [here](https://www.elastic.co/guide/en/beats/libbeat/5.6/kibana-installation.html).

&nbsp; 

### 4. Application Server (Netty)

* Download the version 0.8.1

```
wget https://webdav-r3lab.uni.lu/public/ada-artifacts/ada-web-0.8.1.zip
```

* Unzip the server binaries

```sh
sudo apt-get install unzip
unzip ada-web-$VERSION.zip
cd ada-web-$VERSION/bin
```

* Create a temp folder wherever you want,  e.g.

```
mkdir ada_temp
```

* Set the path and memory constraint variables `ADA_TEMP` and `ADA_MEM` in `runme` script (Ada's `bin` folder).

* Configure DB connections in `set_env.sh` (`bin` folder)

1 . *Mongo*
     
if not password-protected (default) set `ADA_MONGO_DB_URI`

```sh
export ADA_MONGO_DB_URI="mongodb://localhost:27017/ada?rm.nbChannelsPerNode=20"
```
     
 if password-protected set the following variables


```
export ADA_MONGO_DB_HOST=x.x.x.x:27017
export ADA_MONGO_DB_NAME="ada"
export ADA_MONGO_DB_USERNAME="xxx_user"
export ADA_MONGO_DB_PASSWORD="XXX"
```

2 . *Elastic Search*

if non-localhost server is used set `ADA_ELASTIC_DB_HOST`

```
export ADA_ELASTIC_DB_HOST=x.x.x.x:9200
```

Also set the custom ES cluster name if it was configured in Section 3 (see `/etc/elasticsearch/elasticsearch.yml`) 

```
export ADA_ELASTIC_DB_CLUSTER_NAME="ada-cluster"
```

3 . *General Setting*


* Configure `project`, `ldap` (to enable access **without** authentication), and `datasetimport` in `custom.conf` (Ada `conf` folder) 

```sh
project {
  name = "Ultimate"
  url = "https://ada-discovery.github.io"
  logo = "images/logos/ada_logo_v4.png"
}

ldap {
  mode = "local"
  port = "65505"
  debugusers = true
}

datasetimport.import.folder = "/custom_path"
```

* Switch a Netty transport implementation to `jdk` (in `custom.conf`), which is required for MacOS deployments

```sh
play.server.netty.transport = "jdk"
```

* Optionally if you want to use external images not shipped by default with Ada you must register your resource folder(s) placed in the Ada root by editing `custom.conf`

```sh
assets.external_paths = ["/folder_in_classpath"]
```
(*Warning*: Never set any of the folders to "/" since this would make all the classpath files, including common configuration files, accessible from outside as application assets. An example: https://localhost:8080/assets/application.conf)

Now you can use the logo images placed in your `folder_in_classpath` by adapting `custom.conf` for instance as

```
project {
  ....
  logo = "syscid_large.png"
}

footer.logos = [
  {url: "https://www.aetionomy.eu", logo: "aetionomy.png", height: 150},
  {url: "https://www.efpia.eu", logo: "efpia_logo.png"}
]
```
(Note an optional `height` attribute)

* Start Ada (Netty)

```sh
./runme
```
(if cannot be executed `chmod +x runme` might be needed)

* To check if everything works as expected see the log file at `$ADA_ROOT/bin/logs/application.log`

* You can now access Ada at

```sh
http://localhost:8080
```

* To login as admin go to

```sh
http://localhost:8080/loginAdmin
```

* Set your custom `Homepage`, `Contact`, and `Links` in *Admin  →  HTML Snippets → [+]*

`Homepage` example:
```html
<p>
	Ada provides key infrastructure for secured integration, visualization,
	and analysis of heterogeneous clinical and experimental data generated
	during the <a href="http://www.project_url_to_set.com">My TODO project</a>.
</p>
<p>
	My TODO research project focuses on improving ...
</p>
<button type="button" class="btn btn-default btn-sm" data-toggle="collapse" data-target="#more-info">Read More</button>
<div id="more-info" class="collapse">
	<blockquote>
		<h4>The platform currently ...</h4>
	</blockquote>
</div>
<br/>
```
 
`Contact` example:
```html
<strong>Dr. John Snow</strong></br>
Winterfell Team</br>
Westeros Centre For Systems Biomedicine (WCSB)</br>
University of Seven Kingdoms</br></br>
<i class="glyphicon glyphicon-envelope"></i>
<a href="mailto:john.snow@north.edu?Subject=Ada Question">john.snow@north.edu</a><br>
<i class="glyphicon glyphicon-chevron-right"></i>
<a target="_blank" href="http://www.north.edu/wcsb">www.north.edu/wcsb</a><br>
```

`Links` example:
```html
<li><a href="https://www.project-redcap.org">RedCap</a></li>
<li><a href="https://www.synapse.org">Synapse</a></li>
<li role="separator" class="divider"></li>
<li><a href="https://uni.lu/lcsb">LCSB Home</a></li>
```

&nbsp; 

### 5. LDAP

* If your Ada is accessible from outside/the internet or having two users (admin: `/loginAdmin` and basic `/loginBasic`) without authentication is not sufficient you **must** configure LDAP users with authentication
 
* Configure LDAP in `set_env.sh`

```
export ADA_LDAP_HOST="XXX"
export ADA_LDAP_BIND_PASSWORD="XXX"
```
  
* Go to `custom.conf`and remove `mode` (default: remote) and `port` (default: 389) and configure the LDAP groups, example: 

```sh
ldap {
  dit = "cn=users,cn=accounts,dc=north,dc=edu"
  groups = ["cn=my-group-name,cn=groups,cn=accounts,dc=north,dc=edu"]
  bindDN = "uid=my-ldap-reader-xxx,cn=users,cn=accounts,dc=north,dc=edu"
  debugusers = true
}
```
* Restart Ada

```sh
./stopme
```
and
```sh
./runme
```
* Log in to Ada

* Import LDAP users by clicking
*Admin → User Actions → Import from LDAP*

* Assign admin role to at least one user (yourself)
*Admin → Users → double click on a user → Roles → [+] → write `admin` → Update*

* Disable `debugusers` by removing it from `custom.conf` or setting it to `false`

* Restart Ada

* Check if `debugusers` have been disabled by trying to access `http://localhost:8080/loginAdmin` (must not allow you in)

* Login using your LDAP username and password

* Start by importing your first data set in *Admin →  Data Set Imports → [+]*

* Have fun!
