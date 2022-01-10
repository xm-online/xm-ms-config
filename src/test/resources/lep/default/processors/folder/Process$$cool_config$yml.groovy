import com.icthh.xm.commons.config.domain.Configuration

Configuration configuration = lepContext.inArgs.configuration

return [new Configuration(configuration.path, "processed content of: ${configuration.content}")]
