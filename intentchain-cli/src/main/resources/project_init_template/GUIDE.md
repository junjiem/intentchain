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

训练数据应该放在项目根目录下的 `models` 目录中，使用 CSV 格式：

```
models/
├── customers.csv       # 客户训练数据
└── marts/
    ├── orders.csv          # 订单训练数据
    └── products.csv        # 产品训练数据
```

CSV 文件格式（无表头）：
- **第一列**：文本内容（分类的文本）
- **第二列**：标签（意图标签）


### Tests

测试数据应该放在项目根目录下的 `tests` 目录中，使用 CSV 格式：

```
tests/
  ├── test_set1.csv
  ├── test_set2.csv
  └── ...
```

CSV 文件格式（无表头）：
- **第一列**：文本内容（待分类的文本）
- **第二列**：标签（预期的意图标签）

示例 `tests/test_data.csv`：
```csv
我要办理信用卡,办卡
帮我查询余额,查询余额
转账给张三,转账
"我的卡被锁了,怎么办?",挂失
```


## Run the Project

After all, just run your project by running the following command:

```bash
ichain run -p ./ROOT_DIRECTORY_OF_YOUR_PROJECT
```

Start the interactive classification command line.

> **Note:** Incremental build will be automatically performed before run.


## Test the Project

```bash
ichain test -p ./ROOT_DIRECTORY_OF_YOUR_PROJECT
```
