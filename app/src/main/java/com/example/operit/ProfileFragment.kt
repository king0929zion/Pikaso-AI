package com.example.operit

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import java.io.File
import java.io.FileOutputStream

class ProfileFragment : Fragment() {

    private lateinit var ivAvatar: ImageView
    private lateinit var etNickname: TextInputEditText
    private var selectedAvatarBitmap: Bitmap? = null

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                loadImageFromUri(uri)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val toolbar = view.findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }

        ivAvatar = view.findViewById(R.id.ivAvatar)
        etNickname = view.findViewById(R.id.etNickname)

        view.findViewById<View>(R.id.btnEditAvatar).setOnClickListener { pickImage() }
        ivAvatar.setOnClickListener { pickImage() }

        view.findViewById<MaterialButton>(R.id.btnSave).setOnClickListener { save() }

        loadProfile()
    }

    private fun loadProfile() {
        val ctx = context ?: return
        val prefs = ctx.getSharedPreferences("user_profile", android.content.Context.MODE_PRIVATE)
        
        val nickname = prefs.getString("nickname", "") ?: ""
        etNickname.setText(nickname)

        val avatarPath = prefs.getString("avatar_path", null)
        if (avatarPath != null) {
            val file = File(avatarPath)
            if (file.exists()) {
                val bmp = BitmapFactory.decodeFile(file.absolutePath)
                if (bmp != null) {
                    ivAvatar.setImageBitmap(bmp)
                    selectedAvatarBitmap = bmp
                }
            }
        }
    }

    private fun pickImage() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        pickImageLauncher.launch(intent)
    }

    private fun loadImageFromUri(uri: Uri) {
        val ctx = context ?: return
        try {
            ctx.contentResolver.openInputStream(uri)?.use { inputStream ->
                val bmp = BitmapFactory.decodeStream(inputStream)
                if (bmp != null) {
                    selectedAvatarBitmap = bmp
                    ivAvatar.setImageBitmap(bmp)
                }
            }
        } catch (e: Exception) {
            Toast.makeText(ctx, "无法加载图片", Toast.LENGTH_SHORT).show()
        }
    }

    private fun save() {
        val ctx = context ?: return
        val nickname = etNickname.text?.toString()?.trim() ?: ""

        val prefs = ctx.getSharedPreferences("user_profile", android.content.Context.MODE_PRIVATE)
        val editor = prefs.edit()
        editor.putString("nickname", nickname)

        // Save avatar if changed
        selectedAvatarBitmap?.let { bmp ->
            try {
                val avatarFile = File(ctx.filesDir, "user_avatar.png")
                FileOutputStream(avatarFile).use { out ->
                    bmp.compress(Bitmap.CompressFormat.PNG, 90, out)
                }
                editor.putString("avatar_path", avatarFile.absolutePath)
            } catch (e: Exception) {
                Toast.makeText(ctx, "保存头像失败", Toast.LENGTH_SHORT).show()
            }
        }

        editor.apply()
        Toast.makeText(ctx, "个人资料已保存", Toast.LENGTH_SHORT).show()
        parentFragmentManager.popBackStack()
    }
}
