import { StyleProvider } from '@ant-design/cssinjs'
import { App as AntdApp, ConfigProvider } from 'antd'
import viVN from 'antd/locale/vi_VN'
import { RouterProvider } from 'react-router-dom'
import { router } from './router'

export default function App() {
  return (
    // layer: đưa style của Ant Design vào @layer antd để cùng tồn tại với Tailwind (xem src/index.css)
    <StyleProvider layer>
      <ConfigProvider
        locale={viVN}
        theme={{ token: { colorPrimary: '#1677ff', borderRadius: 8 } }}
      >
        <AntdApp>
          <RouterProvider router={router} />
        </AntdApp>
      </ConfigProvider>
    </StyleProvider>
  )
}
