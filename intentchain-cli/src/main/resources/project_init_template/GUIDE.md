## User Guide of how to develop a IntentChain Project

Hi there, looks like you have already created a Project, now let's get you started with the development!

I believe you have the right project configurations for your Project while initialization it, if not, you can change it later by modifying the `project.yaml` file.


### Project YAML

Now you can edit the `project.yaml` file to describe your Project, here is the basic structure of it:

- version (integer, optional): Project's version
- name (string, required): Project's name
- description (string, optional): Project's description
- configuration (object, optional): Project's configuration options
- chain (list\[string\], required): The array of names of the specified classifiers forms a cascading chain
- llm (object, optional): Large language models configurations
  - provider (string, required): Large language model factory identifier
  - configuration (object, optional): Configuration options
- embedding (object, optional): Embedding model configuration
  - provider (string, required): Embedding model factory identifier
  - configuration (object, optional): Configuration options
- embedding_store (object, optional): Embedding store configuration
  - provider (string, required): Embedding store factory identifier
  - configuration (object, optional): Configuration options
- reranking (object, optional): Scoring (reranking) model configuration
  - provider (string, required): Scoring (reranking) model factory identifier
  - configuration (object, optional): Configuration options
- classifiers (list\[object\], required): Intent classifiers configurations
  - name (string, required): Classifier's name
  - description (string, optional): Classifier's description
  - provider (string, required): Intent classifier factory identifier
  - configuration (object, optional): Configuration options


> **Note:** For more project configurations, please refer to [`project.yaml.template`](./project.yaml.template).


### Models

* 在`models`目录下的`.csv`文件为训练数据，第一列为文本内容，第二列为标签（label）。
* 每行对应一条训练数据，通过CSV文件进行定义

#### 文件结构和组织

项目推荐以下文件组织方式：

```
models/
├── customers.csv       # 客户训练数据
└── marts/
    ├── orders.csv          # 订单训练数据
    └── products.csv        # 产品训练数据
```


### Tests



## Run Agent specified in the Project

After all, just run your Agent by running the following command:

```bash
ichain run -p ./ROOT_DIRECTORY_OF_YOUR_PROJECT
```

Start the interactive classification command line.

> **Note:** Incremental build will be automatically performed before run.

