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
        theme={{
          token: {
            colorPrimary: '#2563eb', // Industrial cobalt blue - sắc nét, tin cậy cho hệ thống kho
            colorSuccess: '#16a34a',
            colorWarning: '#d97706',
            colorError: '#dc2626',
            colorInfo: '#2563eb',
            colorTextBase: '#0f172a',
            colorTextSecondary: '#475569',
            colorTextTertiary: '#94a3b8',
            colorBgBase: '#ffffff',
            colorBgLayout: '#f8fafc',
            colorBorder: '#e2e8f0',
            colorBorderSecondary: '#f1f5f9',
            borderRadius: 6,
            borderRadiusLG: 8,
            borderRadiusSM: 4,
            fontFamily:
              "'Roboto', -apple-system, BlinkMacSystemFont, 'Segoe UI', Arial, sans-serif",
            fontSize: 14,
            controlHeight: 36,
          },
          components: {
            Layout: {
              headerBg: '#ffffff',
              headerHeight: 56,
              headerPadding: '0 24px',
              siderBg: '#0f172a', // Graphite / Slate 900 trầm tĩnh
              bodyBg: '#f8fafc',
            },
            Menu: {
              darkItemBg: '#0f172a',
              darkSubMenuItemBg: '#0b1120',
              darkItemSelectedBg: '#1e293b',
              darkItemSelectedColor: '#ffffff',
              darkItemHoverBg: '#1e293b',
              darkItemColor: '#94a3b8',
              itemBorderRadius: 6,
              itemMarginInline: 8,
              itemMarginBlock: 3,
              itemHeight: 38,
            },
            Card: {
              colorBorderSecondary: '#e2e8f0',
              paddingLG: 20,
            },
            Table: {
              headerBg: '#f8fafc',
              headerColor: '#475569',
              rowHoverBg: '#f8fafc',
              borderColor: '#e2e8f0',
              cellPaddingBlock: 12,
              cellPaddingInline: 12,
            },
            Button: {
              controlHeight: 36,
              borderRadius: 6,
              fontWeight: 500,
            },
            Input: {
              controlHeight: 36,
              borderRadius: 6,
              colorBorder: '#cbd5e1',
            },
            Select: {
              controlHeight: 36,
              borderRadius: 6,
              colorBorder: '#cbd5e1',
            },
          },
        }}
      >
        <AntdApp>
          <RouterProvider router={router} />
        </AntdApp>
      </ConfigProvider>
    </StyleProvider>
  )
}
