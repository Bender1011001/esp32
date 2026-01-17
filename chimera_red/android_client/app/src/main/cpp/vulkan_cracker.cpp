// Chimera Red - Vulkan PBKDF2 Compute Engine v2.0
// High-performance GPU-accelerated password cracking for WPA2
//
// Architecture:
//   JNI Host (this file) <-> Vulkan Compute Pipeline <-> SPIR-V Shader
//
// Performance Target: 50,000+ H/s on Adreno 750 (S24 Ultra)

#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include <android/log.h>
#include <chrono>
#include <cstring>
#include <jni.h>
#include <string>
#include <vector>
#include <vulkan/vulkan.h>


#define LOG_TAG "VulkanCracker"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// ============================================================================
// SHA1 CONSTANTS
// ============================================================================
static const uint32_t SHA1_H0 = 0x67452301;
static const uint32_t SHA1_H1 = 0xEFCDAB89;
static const uint32_t SHA1_H2 = 0x98BADCFE;
static const uint32_t SHA1_H3 = 0x10325476;
static const uint32_t SHA1_H4 = 0xC3D2E1F0;

// ============================================================================
// PUSH CONSTANTS for compute shader
// ============================================================================
struct PushConstants {
  uint32_t passwordCount;
  uint32_t iterations;
};

// ============================================================================
// VULKAN ENGINE STATE
// ============================================================================
struct VulkanEngine {
  VkInstance instance = VK_NULL_HANDLE;
  VkPhysicalDevice physicalDevice = VK_NULL_HANDLE;
  VkDevice device = VK_NULL_HANDLE;
  VkQueue computeQueue = VK_NULL_HANDLE;
  uint32_t computeQueueFamily = 0;

  VkCommandPool commandPool = VK_NULL_HANDLE;
  VkCommandBuffer commandBuffer = VK_NULL_HANDLE;

  VkDescriptorSetLayout descriptorSetLayout = VK_NULL_HANDLE;
  VkDescriptorPool descriptorPool = VK_NULL_HANDLE;
  VkDescriptorSet descriptorSet = VK_NULL_HANDLE;

  VkPipelineLayout pipelineLayout = VK_NULL_HANDLE;
  VkPipeline pipeline = VK_NULL_HANDLE;
  VkShaderModule shaderModule = VK_NULL_HANDLE;

  // Buffers
  VkBuffer passwordBuffer = VK_NULL_HANDLE;
  VkBuffer saltBuffer = VK_NULL_HANDLE;
  VkBuffer outputBuffer = VK_NULL_HANDLE;
  VkDeviceMemory passwordMemory = VK_NULL_HANDLE;
  VkDeviceMemory saltMemory = VK_NULL_HANDLE;
  VkDeviceMemory outputMemory = VK_NULL_HANDLE;

  // Buffer sizes
  VkDeviceSize passwordBufferSize = 0;
  VkDeviceSize saltBufferSize = 0;
  VkDeviceSize outputBufferSize = 0;

  bool initialized = false;
  bool pipelineReady = false;
  uint32_t maxWorkgroupSize = 64;

  std::string deviceName;

  // Benchmarking
  double lastGpuTimeMs = 0.0;
  double lastCpuTimeMs = 0.0;
  int lastBatchSize = 0;
};

static VulkanEngine g_engine;

// ============================================================================
// HELPER FUNCTIONS
// ============================================================================

static uint32_t findMemoryType(VkPhysicalDevice physDevice, uint32_t typeFilter,
                               VkMemoryPropertyFlags properties) {
  VkPhysicalDeviceMemoryProperties memProperties;
  vkGetPhysicalDeviceMemoryProperties(physDevice, &memProperties);

  for (uint32_t i = 0; i < memProperties.memoryTypeCount; i++) {
    if ((typeFilter & (1 << i)) && (memProperties.memoryTypes[i].propertyFlags &
                                    properties) == properties) {
      return i;
    }
  }
  return UINT32_MAX;
}

static bool createBuffer(VkDevice device, VkPhysicalDevice physDevice,
                         VkDeviceSize size, VkBufferUsageFlags usage,
                         VkMemoryPropertyFlags properties, VkBuffer &buffer,
                         VkDeviceMemory &memory) {
  VkBufferCreateInfo bufferInfo = {};
  bufferInfo.sType = VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO;
  bufferInfo.size = size;
  bufferInfo.usage = usage;
  bufferInfo.sharingMode = VK_SHARING_MODE_EXCLUSIVE;

  if (vkCreateBuffer(device, &bufferInfo, nullptr, &buffer) != VK_SUCCESS) {
    LOGE("Failed to create buffer");
    return false;
  }

  VkMemoryRequirements memRequirements;
  vkGetBufferMemoryRequirements(device, buffer, &memRequirements);

  VkMemoryAllocateInfo allocInfo = {};
  allocInfo.sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
  allocInfo.allocationSize = memRequirements.size;
  allocInfo.memoryTypeIndex =
      findMemoryType(physDevice, memRequirements.memoryTypeBits, properties);

  if (allocInfo.memoryTypeIndex == UINT32_MAX) {
    LOGE("Failed to find suitable memory type");
    return false;
  }

  if (vkAllocateMemory(device, &allocInfo, nullptr, &memory) != VK_SUCCESS) {
    LOGE("Failed to allocate buffer memory");
    return false;
  }

  vkBindBufferMemory(device, buffer, memory, 0);
  return true;
}

// ============================================================================
// VULKAN INITIALIZATION
// ============================================================================

static bool initVulkan() {
  if (g_engine.initialized)
    return true;

  LOGI("Initializing Vulkan compute engine...");

  // 1. Create Instance
  VkApplicationInfo appInfo = {};
  appInfo.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO;
  appInfo.pApplicationName = "ChimeraRedCracker";
  appInfo.applicationVersion = VK_MAKE_VERSION(2, 0, 0);
  appInfo.pEngineName = "VulkanPBKDF2";
  appInfo.engineVersion = VK_MAKE_VERSION(2, 0, 0);
  appInfo.apiVersion = VK_API_VERSION_1_1;

  VkInstanceCreateInfo instanceInfo = {};
  instanceInfo.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
  instanceInfo.pApplicationInfo = &appInfo;

  if (vkCreateInstance(&instanceInfo, nullptr, &g_engine.instance) !=
      VK_SUCCESS) {
    LOGE("Failed to create Vulkan instance");
    return false;
  }

  // 2. Select Physical Device
  uint32_t deviceCount = 0;
  vkEnumeratePhysicalDevices(g_engine.instance, &deviceCount, nullptr);

  if (deviceCount == 0) {
    LOGE("No Vulkan-capable GPU found");
    return false;
  }

  std::vector<VkPhysicalDevice> devices(deviceCount);
  vkEnumeratePhysicalDevices(g_engine.instance, &deviceCount, devices.data());

  g_engine.physicalDevice = devices[0];

  VkPhysicalDeviceProperties deviceProps;
  vkGetPhysicalDeviceProperties(g_engine.physicalDevice, &deviceProps);
  g_engine.deviceName = deviceProps.deviceName;
  g_engine.maxWorkgroupSize = deviceProps.limits.maxComputeWorkGroupSize[0];

  LOGI("Selected GPU: %s (Max Workgroup: %u)", g_engine.deviceName.c_str(),
       g_engine.maxWorkgroupSize);

  // 3. Find Compute Queue Family
  uint32_t queueFamilyCount = 0;
  vkGetPhysicalDeviceQueueFamilyProperties(g_engine.physicalDevice,
                                           &queueFamilyCount, nullptr);

  std::vector<VkQueueFamilyProperties> queueFamilies(queueFamilyCount);
  vkGetPhysicalDeviceQueueFamilyProperties(
      g_engine.physicalDevice, &queueFamilyCount, queueFamilies.data());

  for (uint32_t i = 0; i < queueFamilyCount; i++) {
    if (queueFamilies[i].queueFlags & VK_QUEUE_COMPUTE_BIT) {
      g_engine.computeQueueFamily = i;
      break;
    }
  }

  // 4. Create Logical Device
  float queuePriority = 1.0f;
  VkDeviceQueueCreateInfo queueCreateInfo = {};
  queueCreateInfo.sType = VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO;
  queueCreateInfo.queueFamilyIndex = g_engine.computeQueueFamily;
  queueCreateInfo.queueCount = 1;
  queueCreateInfo.pQueuePriorities = &queuePriority;

  VkDeviceCreateInfo deviceCreateInfo = {};
  deviceCreateInfo.sType = VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO;
  deviceCreateInfo.queueCreateInfoCount = 1;
  deviceCreateInfo.pQueueCreateInfos = &queueCreateInfo;

  if (vkCreateDevice(g_engine.physicalDevice, &deviceCreateInfo, nullptr,
                     &g_engine.device) != VK_SUCCESS) {
    LOGE("Failed to create logical device");
    return false;
  }

  vkGetDeviceQueue(g_engine.device, g_engine.computeQueueFamily, 0,
                   &g_engine.computeQueue);

  // 5. Create Command Pool
  VkCommandPoolCreateInfo poolInfo = {};
  poolInfo.sType = VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO;
  poolInfo.queueFamilyIndex = g_engine.computeQueueFamily;
  poolInfo.flags = VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT;

  if (vkCreateCommandPool(g_engine.device, &poolInfo, nullptr,
                          &g_engine.commandPool) != VK_SUCCESS) {
    LOGE("Failed to create command pool");
    return false;
  }

  // 6. Allocate Command Buffer
  VkCommandBufferAllocateInfo allocInfo = {};
  allocInfo.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO;
  allocInfo.commandPool = g_engine.commandPool;
  allocInfo.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY;
  allocInfo.commandBufferCount = 1;

  if (vkAllocateCommandBuffers(g_engine.device, &allocInfo,
                               &g_engine.commandBuffer) != VK_SUCCESS) {
    LOGE("Failed to allocate command buffer");
    return false;
  }

  g_engine.initialized = true;
  LOGI("Vulkan compute engine initialized successfully");
  return true;
}

// ============================================================================
// CREATE COMPUTE PIPELINE (call after loading shader)
// ============================================================================
static bool createComputePipeline(const uint32_t *shaderCode,
                                  size_t shaderSize) {
  if (!g_engine.initialized)
    return false;

  LOGI("Creating compute pipeline...");

  // 1. Create shader module
  VkShaderModuleCreateInfo shaderInfo = {};
  shaderInfo.sType = VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO;
  shaderInfo.codeSize = shaderSize;
  shaderInfo.pCode = shaderCode;

  if (vkCreateShaderModule(g_engine.device, &shaderInfo, nullptr,
                           &g_engine.shaderModule) != VK_SUCCESS) {
    LOGE("Failed to create shader module");
    return false;
  }

  // 2. Create descriptor set layout (3 storage buffers)
  VkDescriptorSetLayoutBinding bindings[3] = {};

  // Binding 0: Password buffer
  bindings[0].binding = 0;
  bindings[0].descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_BUFFER;
  bindings[0].descriptorCount = 1;
  bindings[0].stageFlags = VK_SHADER_STAGE_COMPUTE_BIT;

  // Binding 1: Salt buffer
  bindings[1].binding = 1;
  bindings[1].descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_BUFFER;
  bindings[1].descriptorCount = 1;
  bindings[1].stageFlags = VK_SHADER_STAGE_COMPUTE_BIT;

  // Binding 2: Output buffer
  bindings[2].binding = 2;
  bindings[2].descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_BUFFER;
  bindings[2].descriptorCount = 1;
  bindings[2].stageFlags = VK_SHADER_STAGE_COMPUTE_BIT;

  VkDescriptorSetLayoutCreateInfo layoutInfo = {};
  layoutInfo.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO;
  layoutInfo.bindingCount = 3;
  layoutInfo.pBindings = bindings;

  if (vkCreateDescriptorSetLayout(g_engine.device, &layoutInfo, nullptr,
                                  &g_engine.descriptorSetLayout) !=
      VK_SUCCESS) {
    LOGE("Failed to create descriptor set layout");
    return false;
  }

  // 3. Create pipeline layout with push constants
  VkPushConstantRange pushConstantRange = {};
  pushConstantRange.stageFlags = VK_SHADER_STAGE_COMPUTE_BIT;
  pushConstantRange.offset = 0;
  pushConstantRange.size = sizeof(PushConstants);

  VkPipelineLayoutCreateInfo pipelineLayoutInfo = {};
  pipelineLayoutInfo.sType = VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO;
  pipelineLayoutInfo.setLayoutCount = 1;
  pipelineLayoutInfo.pSetLayouts = &g_engine.descriptorSetLayout;
  pipelineLayoutInfo.pushConstantRangeCount = 1;
  pipelineLayoutInfo.pPushConstantRanges = &pushConstantRange;

  if (vkCreatePipelineLayout(g_engine.device, &pipelineLayoutInfo, nullptr,
                             &g_engine.pipelineLayout) != VK_SUCCESS) {
    LOGE("Failed to create pipeline layout");
    return false;
  }

  // 4. Create compute pipeline
  VkComputePipelineCreateInfo pipelineInfo = {};
  pipelineInfo.sType = VK_STRUCTURE_TYPE_COMPUTE_PIPELINE_CREATE_INFO;
  pipelineInfo.stage.sType =
      VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
  pipelineInfo.stage.stage = VK_SHADER_STAGE_COMPUTE_BIT;
  pipelineInfo.stage.module = g_engine.shaderModule;
  pipelineInfo.stage.pName = "main";
  pipelineInfo.layout = g_engine.pipelineLayout;

  if (vkCreateComputePipelines(g_engine.device, VK_NULL_HANDLE, 1,
                               &pipelineInfo, nullptr,
                               &g_engine.pipeline) != VK_SUCCESS) {
    LOGE("Failed to create compute pipeline");
    return false;
  }

  // 5. Create descriptor pool
  VkDescriptorPoolSize poolSize = {};
  poolSize.type = VK_DESCRIPTOR_TYPE_STORAGE_BUFFER;
  poolSize.descriptorCount = 3;

  VkDescriptorPoolCreateInfo descriptorPoolInfo = {};
  descriptorPoolInfo.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO;
  descriptorPoolInfo.maxSets = 1;
  descriptorPoolInfo.poolSizeCount = 1;
  descriptorPoolInfo.pPoolSizes = &poolSize;

  if (vkCreateDescriptorPool(g_engine.device, &descriptorPoolInfo, nullptr,
                             &g_engine.descriptorPool) != VK_SUCCESS) {
    LOGE("Failed to create descriptor pool");
    return false;
  }

  // 6. Allocate descriptor set
  VkDescriptorSetAllocateInfo descriptorAllocInfo = {};
  descriptorAllocInfo.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO;
  descriptorAllocInfo.descriptorPool = g_engine.descriptorPool;
  descriptorAllocInfo.descriptorSetCount = 1;
  descriptorAllocInfo.pSetLayouts = &g_engine.descriptorSetLayout;

  if (vkAllocateDescriptorSets(g_engine.device, &descriptorAllocInfo,
                               &g_engine.descriptorSet) != VK_SUCCESS) {
    LOGE("Failed to allocate descriptor set");
    return false;
  }

  g_engine.pipelineReady = true;
  LOGI("Compute pipeline created successfully");
  return true;
}

// ============================================================================
// ALLOCATE BUFFERS
// ============================================================================
static bool allocateBuffers(uint32_t maxPasswords) {
  if (!g_engine.initialized)
    return false;

  // Password buffer: 64 bytes per password (length byte + 63 chars)
  g_engine.passwordBufferSize = maxPasswords * 64;

  // Salt buffer: 36 bytes (4 bytes length + 32 bytes salt)
  g_engine.saltBufferSize = 36;

  // Output buffer: 32 bytes per password (PMK)
  g_engine.outputBufferSize = maxPasswords * 32;

  VkMemoryPropertyFlags memProps = VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT |
                                   VK_MEMORY_PROPERTY_HOST_COHERENT_BIT;

  if (!createBuffer(g_engine.device, g_engine.physicalDevice,
                    g_engine.passwordBufferSize,
                    VK_BUFFER_USAGE_STORAGE_BUFFER_BIT, memProps,
                    g_engine.passwordBuffer, g_engine.passwordMemory)) {
    return false;
  }

  if (!createBuffer(g_engine.device, g_engine.physicalDevice,
                    g_engine.saltBufferSize, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT,
                    memProps, g_engine.saltBuffer, g_engine.saltMemory)) {
    return false;
  }

  if (!createBuffer(g_engine.device, g_engine.physicalDevice,
                    g_engine.outputBufferSize,
                    VK_BUFFER_USAGE_STORAGE_BUFFER_BIT, memProps,
                    g_engine.outputBuffer, g_engine.outputMemory)) {
    return false;
  }

  // Update descriptor set
  VkDescriptorBufferInfo bufferInfos[3] = {};
  bufferInfos[0].buffer = g_engine.passwordBuffer;
  bufferInfos[0].offset = 0;
  bufferInfos[0].range = g_engine.passwordBufferSize;

  bufferInfos[1].buffer = g_engine.saltBuffer;
  bufferInfos[1].offset = 0;
  bufferInfos[1].range = g_engine.saltBufferSize;

  bufferInfos[2].buffer = g_engine.outputBuffer;
  bufferInfos[2].offset = 0;
  bufferInfos[2].range = g_engine.outputBufferSize;

  VkWriteDescriptorSet writes[3] = {};
  for (int i = 0; i < 3; i++) {
    writes[i].sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET;
    writes[i].dstSet = g_engine.descriptorSet;
    writes[i].dstBinding = i;
    writes[i].dstArrayElement = 0;
    writes[i].descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_BUFFER;
    writes[i].descriptorCount = 1;
    writes[i].pBufferInfo = &bufferInfos[i];
  }

  vkUpdateDescriptorSets(g_engine.device, 3, writes, 0, nullptr);

  LOGI("Buffers allocated: passwords=%zu, salt=%zu, output=%zu",
       (size_t)g_engine.passwordBufferSize, (size_t)g_engine.saltBufferSize,
       (size_t)g_engine.outputBufferSize);

  return true;
}

// ============================================================================
// CLEANUP
// ============================================================================

static void cleanupVulkan() {
  if (!g_engine.initialized)
    return;

  vkDeviceWaitIdle(g_engine.device);

  if (g_engine.pipeline)
    vkDestroyPipeline(g_engine.device, g_engine.pipeline, nullptr);
  if (g_engine.pipelineLayout)
    vkDestroyPipelineLayout(g_engine.device, g_engine.pipelineLayout, nullptr);
  if (g_engine.shaderModule)
    vkDestroyShaderModule(g_engine.device, g_engine.shaderModule, nullptr);
  if (g_engine.descriptorPool)
    vkDestroyDescriptorPool(g_engine.device, g_engine.descriptorPool, nullptr);
  if (g_engine.descriptorSetLayout)
    vkDestroyDescriptorSetLayout(g_engine.device, g_engine.descriptorSetLayout,
                                 nullptr);

  if (g_engine.passwordBuffer)
    vkDestroyBuffer(g_engine.device, g_engine.passwordBuffer, nullptr);
  if (g_engine.saltBuffer)
    vkDestroyBuffer(g_engine.device, g_engine.saltBuffer, nullptr);
  if (g_engine.outputBuffer)
    vkDestroyBuffer(g_engine.device, g_engine.outputBuffer, nullptr);
  if (g_engine.passwordMemory)
    vkFreeMemory(g_engine.device, g_engine.passwordMemory, nullptr);
  if (g_engine.saltMemory)
    vkFreeMemory(g_engine.device, g_engine.saltMemory, nullptr);
  if (g_engine.outputMemory)
    vkFreeMemory(g_engine.device, g_engine.outputMemory, nullptr);

  if (g_engine.commandPool)
    vkDestroyCommandPool(g_engine.device, g_engine.commandPool, nullptr);
  if (g_engine.device)
    vkDestroyDevice(g_engine.device, nullptr);
  if (g_engine.instance)
    vkDestroyInstance(g_engine.instance, nullptr);

  g_engine = VulkanEngine();
  LOGI("Vulkan engine cleaned up");
}

// ============================================================================
// CPU FALLBACK: PBKDF2-HMAC-SHA1
// ============================================================================

static void sha1_transform(uint32_t state[5], const uint8_t block[64]) {
  uint32_t w[80];

  for (int i = 0; i < 16; i++) {
    w[i] = (block[i * 4] << 24) | (block[i * 4 + 1] << 16) |
           (block[i * 4 + 2] << 8) | block[i * 4 + 3];
  }
  for (int i = 16; i < 80; i++) {
    uint32_t temp = w[i - 3] ^ w[i - 8] ^ w[i - 14] ^ w[i - 16];
    w[i] = (temp << 1) | (temp >> 31);
  }

  uint32_t a = state[0], b = state[1], c = state[2], d = state[3], e = state[4];

  for (int i = 0; i < 80; i++) {
    uint32_t f, k;
    if (i < 20) {
      f = (b & c) | ((~b) & d);
      k = 0x5A827999;
    } else if (i < 40) {
      f = b ^ c ^ d;
      k = 0x6ED9EBA1;
    } else if (i < 60) {
      f = (b & c) | (b & d) | (c & d);
      k = 0x8F1BBCDC;
    } else {
      f = b ^ c ^ d;
      k = 0xCA62C1D6;
    }

    uint32_t temp = ((a << 5) | (a >> 27)) + f + e + k + w[i];
    e = d;
    d = c;
    c = (b << 30) | (b >> 2);
    b = a;
    a = temp;
  }

  state[0] += a;
  state[1] += b;
  state[2] += c;
  state[3] += d;
  state[4] += e;
}

static void sha1(const uint8_t *data, size_t len, uint8_t out[20]) {
  uint32_t state[5] = {SHA1_H0, SHA1_H1, SHA1_H2, SHA1_H3, SHA1_H4};
  uint8_t block[64];
  size_t i;

  for (i = 0; i + 64 <= len; i += 64) {
    sha1_transform(state, data + i);
  }

  size_t remaining = len - i;
  memcpy(block, data + i, remaining);
  block[remaining] = 0x80;

  if (remaining >= 56) {
    memset(block + remaining + 1, 0, 63 - remaining);
    sha1_transform(state, block);
    memset(block, 0, 56);
  } else {
    memset(block + remaining + 1, 0, 55 - remaining);
  }

  uint64_t bits = len * 8;
  for (int j = 0; j < 8; j++) {
    block[56 + j] = (bits >> (56 - j * 8)) & 0xFF;
  }
  sha1_transform(state, block);

  for (int j = 0; j < 5; j++) {
    out[j * 4] = (state[j] >> 24) & 0xFF;
    out[j * 4 + 1] = (state[j] >> 16) & 0xFF;
    out[j * 4 + 2] = (state[j] >> 8) & 0xFF;
    out[j * 4 + 3] = state[j] & 0xFF;
  }
}

static void hmac_sha1(const uint8_t *key, size_t keyLen, const uint8_t *data,
                      size_t dataLen, uint8_t out[20]) {
  uint8_t k[64] = {0};
  uint8_t ipad[64], opad[64];

  if (keyLen > 64) {
    sha1(key, keyLen, k);
  } else {
    memcpy(k, key, keyLen);
  }

  for (int i = 0; i < 64; i++) {
    ipad[i] = k[i] ^ 0x36;
    opad[i] = k[i] ^ 0x5C;
  }

  std::vector<uint8_t> innerData(64 + dataLen);
  memcpy(innerData.data(), ipad, 64);
  memcpy(innerData.data() + 64, data, dataLen);

  uint8_t innerHash[20];
  sha1(innerData.data(), innerData.size(), innerHash);

  uint8_t outerData[64 + 20];
  memcpy(outerData, opad, 64);
  memcpy(outerData + 64, innerHash, 20);

  sha1(outerData, sizeof(outerData), out);
}

static void pbkdf2_sha1(const char *password, size_t passLen,
                        const uint8_t *salt, size_t saltLen,
                        uint32_t iterations, uint32_t dkLen, uint8_t *out) {
  uint32_t blockNum = 1;
  size_t offset = 0;

  while (offset < dkLen) {
    std::vector<uint8_t> saltBlock(saltLen + 4);
    memcpy(saltBlock.data(), salt, saltLen);
    saltBlock[saltLen] = (blockNum >> 24) & 0xFF;
    saltBlock[saltLen + 1] = (blockNum >> 16) & 0xFF;
    saltBlock[saltLen + 2] = (blockNum >> 8) & 0xFF;
    saltBlock[saltLen + 3] = blockNum & 0xFF;

    uint8_t U[20], T[20];
    hmac_sha1((const uint8_t *)password, passLen, saltBlock.data(),
              saltBlock.size(), U);
    memcpy(T, U, 20);

    for (uint32_t i = 1; i < iterations; i++) {
      hmac_sha1((const uint8_t *)password, passLen, U, 20, U);
      for (int j = 0; j < 20; j++) {
        T[j] ^= U[j];
      }
    }

    size_t copyLen = (dkLen - offset < 20) ? (dkLen - offset) : 20;
    memcpy(out + offset, T, copyLen);
    offset += copyLen;
    blockNum++;
  }
}

// ============================================================================
// JNI INTERFACE
// ============================================================================

extern "C" {

JNIEXPORT jboolean JNICALL Java_com_chimera_red_crypto_VulkanCracker_nativeInit(
    JNIEnv *env, jobject thiz) {
  return initVulkan() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL Java_com_chimera_red_crypto_VulkanCracker_nativeCleanup(
    JNIEnv *env, jobject thiz) {
  cleanupVulkan();
}

JNIEXPORT jstring JNICALL
Java_com_chimera_red_crypto_VulkanCracker_nativeGetDeviceName(JNIEnv *env,
                                                              jobject thiz) {
  if (g_engine.initialized) {
    return env->NewStringUTF(g_engine.deviceName.c_str());
  }
  return env->NewStringUTF("Not Initialized");
}

JNIEXPORT jboolean JNICALL
Java_com_chimera_red_crypto_VulkanCracker_nativeIsAvailable(JNIEnv *env,
                                                            jobject thiz) {
  return g_engine.initialized ? JNI_TRUE : JNI_FALSE;
}

// Single PMK derivation (CPU)
JNIEXPORT jbyteArray JNICALL
Java_com_chimera_red_crypto_VulkanCracker_nativeDerivePMK(JNIEnv *env,
                                                          jobject thiz,
                                                          jstring password,
                                                          jstring ssid) {

  const char *pass = env->GetStringUTFChars(password, nullptr);
  const char *salt = env->GetStringUTFChars(ssid, nullptr);
  size_t passLen = strlen(pass);
  size_t saltLen = strlen(salt);

  uint8_t pmk[32];
  pbkdf2_sha1(pass, passLen, (const uint8_t *)salt, saltLen, 4096, 32, pmk);

  env->ReleaseStringUTFChars(password, pass);
  env->ReleaseStringUTFChars(ssid, salt);

  jbyteArray result = env->NewByteArray(32);
  env->SetByteArrayRegion(result, 0, 32, (jbyte *)pmk);
  return result;
}

// Batch PMK derivation with benchmarking
JNIEXPORT jobjectArray JNICALL
Java_com_chimera_red_crypto_VulkanCracker_nativeBatchDerivePMK(
    JNIEnv *env, jobject thiz, jobjectArray passwords, jstring ssid) {

  auto startTime = std::chrono::high_resolution_clock::now();

  const char *salt = env->GetStringUTFChars(ssid, nullptr);
  size_t saltLen = strlen(salt);

  jsize count = env->GetArrayLength(passwords);
  g_engine.lastBatchSize = count;

  jclass byteArrayClass = env->FindClass("[B");
  jobjectArray results = env->NewObjectArray(count, byteArrayClass, nullptr);

  // CPU path (GPU dispatch would go here when pipeline is ready)
  for (jsize i = 0; i < count; i++) {
    jstring passStr = (jstring)env->GetObjectArrayElement(passwords, i);
    const char *pass = env->GetStringUTFChars(passStr, nullptr);
    size_t passLen = strlen(pass);

    uint8_t pmk[32];
    pbkdf2_sha1(pass, passLen, (const uint8_t *)salt, saltLen, 4096, 32, pmk);

    env->ReleaseStringUTFChars(passStr, pass);

    jbyteArray pmkArray = env->NewByteArray(32);
    env->SetByteArrayRegion(pmkArray, 0, 32, (jbyte *)pmk);
    env->SetObjectArrayElement(results, i, pmkArray);
  }

  env->ReleaseStringUTFChars(ssid, salt);

  auto endTime = std::chrono::high_resolution_clock::now();
  g_engine.lastCpuTimeMs =
      std::chrono::duration<double, std::milli>(endTime - startTime).count();

  double hps = (count * 1000.0) / g_engine.lastCpuTimeMs;
  LOGI("Batch complete: %d passwords in %.2f ms (%.0f H/s)", count,
       g_engine.lastCpuTimeMs, hps);

  return results;
}

// Benchmark function
JNIEXPORT jdoubleArray JNICALL
Java_com_chimera_red_crypto_VulkanCracker_nativeBenchmark(JNIEnv *env,
                                                          jobject thiz,
                                                          jint iterations,
                                                          jstring ssid) {

  const char *salt = env->GetStringUTFChars(ssid, nullptr);
  size_t saltLen = strlen(salt);

  // Test passwords
  const char *testPasswords[] = {"password123", "qwerty12345", "letmein2024",
                                 "admin12345",  "welcome123",  "monkey1234",
                                 "dragon2024",  "master123"};
  int numPasswords = 8;

  // Warm up
  uint8_t pmk[32];
  pbkdf2_sha1(testPasswords[0], strlen(testPasswords[0]), (const uint8_t *)salt,
              saltLen, 4096, 32, pmk);

  // Benchmark
  auto startTime = std::chrono::high_resolution_clock::now();

  for (int iter = 0; iter < iterations; iter++) {
    for (int i = 0; i < numPasswords; i++) {
      pbkdf2_sha1(testPasswords[i], strlen(testPasswords[i]),
                  (const uint8_t *)salt, saltLen, 4096, 32, pmk);
    }
  }

  auto endTime = std::chrono::high_resolution_clock::now();
  double totalMs =
      std::chrono::duration<double, std::milli>(endTime - startTime).count();

  int totalHashes = iterations * numPasswords;
  double hps = (totalHashes * 1000.0) / totalMs;
  double avgTimePerHash = totalMs / totalHashes;

  env->ReleaseStringUTFChars(ssid, salt);

  LOGI("Benchmark: %d hashes in %.2f ms = %.0f H/s (%.2f ms/hash)", totalHashes,
       totalMs, hps, avgTimePerHash);

  // Return [totalMs, hps, avgTimePerHash]
  jdoubleArray result = env->NewDoubleArray(3);
  jdouble data[3] = {totalMs, hps, avgTimePerHash};
  env->SetDoubleArrayRegion(result, 0, 3, data);

  return result;
}

// Pipeline status
JNIEXPORT jboolean JNICALL
Java_com_chimera_red_crypto_VulkanCracker_nativeIsPipelineReady(JNIEnv *env,
                                                                jobject thiz) {
  return g_engine.pipelineReady ? JNI_TRUE : JNI_FALSE;
}

} // extern "C"
